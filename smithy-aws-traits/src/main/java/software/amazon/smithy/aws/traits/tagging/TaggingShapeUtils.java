/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.aws.traits.tagging;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.PropertyBindingIndex;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Logic for validating that a shape looks like a tag.
 */
final class TaggingShapeUtils {
    static final String TAG_RESOURCE_OPNAME = "TagResource";
    static final String UNTAG_RESOURCE_OPNAME = "UntagResource";
    static final String LIST_TAGS_OPNAME = "ListTagsForResource";

    private static final Pattern TAG_PROPERTY_REGEX = Pattern
            .compile("^[T|t]ag(s|[L|l]ist)$");
    private static final Pattern RESOURCE_ARN_REGEX = Pattern
            .compile("^([R|r]esource)?([A|a]rn|ARN)$");
    private static final Pattern TAG_KEYS_REGEX = Pattern
            .compile("^[T|t]ag[K|k]eys$");

    private TaggingShapeUtils() {}

    // Recommended name is more limited than the accepted regular expression.
    static String getDesiredTagsPropertyName() {
        return "[T|t]ags";
    }

    // Recommended name is more limited than the accepted regular expression.
    static String getDesiredArnName() {
        return "[R|r]esourceArn";
    }

    // Recommended name is more limited than the accepted regular expression.
    static String getDesiredTagKeysName() {
        return "[T|t]agKeys";
    }

    // Used to validate tag property name and tag member name.
    static boolean isTagDesiredName(String memberName) {
        return TAG_PROPERTY_REGEX.matcher(memberName).matches();
    }

    // Used for checking if member name is good for resource ARN input.
    static boolean isArnMemberDesiredName(String memberName) {
        return RESOURCE_ARN_REGEX.matcher(memberName).matches();
    }

    // Used for checking if member name is good for tag keys input for untag operation.
    static boolean isTagKeysDesiredName(String memberName) {
        return TAG_KEYS_REGEX.matcher(memberName).matches();
    }

    static boolean hasResourceArnInput(Map<String, MemberShape> inputMembers, Model model) {
        return inputMembers.entrySet().stream().filter(memberEntry ->
            TaggingShapeUtils.isArnMemberDesiredName(memberEntry.getKey())
            && model.expectShape(memberEntry.getValue().getTarget()).isStringShape()
        ).count() == 1;
    }

    /**
     * Returns true if and only if a provided shape meets criteria that appears to
     * represent a collection or map of tag key value pairs.
     *
     * @param model Model to retrieve target shapes from when examining
     *              targets.
     * @param tagShape shape to examine if it appears to be a TagList.
     * @return true if and only if shape meets the criteria for being a TagList
     */
    static boolean verifyTagsShape(Model model, Shape tagShape) {
        return verifyTagListShape(model, tagShape) || verifyTagMapShape(model, tagShape);
    }

    static boolean verifyTagListShape(Model model, Shape tagShape) {
        if (tagShape.isListShape()) {
            ListShape listShape = tagShape.asListShape().get();
            Shape listTargetShape = model.expectShape(listShape.getMember().getTarget());
            if (listTargetShape.isStructureShape()) {
                StructureShape memberStructureShape = listTargetShape.asStructureShape().get();
                //Verify member count is two, and both point to string types.
                if (memberStructureShape.members().size() == 2) {
                    boolean allStrings = true;
                    for (MemberShape member : memberStructureShape.members()) {
                        allStrings = allStrings || model.expectShape(member.getTarget()).isStringShape();
                    }
                    return allStrings;
                }
            }
        }
        return false;
    }

    static boolean verifyTagMapShape(Model model, Shape tagShape) {
        if (tagShape.isMapShape()) {
            MapShape mapShape = tagShape.asMapShape().get();
            Shape valueTargetShape = model.expectShape(mapShape.getValue().getTarget());
            return valueTargetShape.isStringShape();
        }
        return false;
    }

    static boolean verifyTagKeysShape(Model model, Shape tagShape) {
        // A list or set that targets a string shape qualifies as listing tag keys
        return (tagShape.isListShape()
                    && model.expectShape(tagShape.asListShape().get().getMember().getTarget()).isStringShape())
                || (tagShape.isSetShape()
                    && model.expectShape(tagShape.asSetShape().get().getMember().getTarget()).isStringShape());
    }

    static boolean verifyTagResourceOperation(Model model, ServiceShape service, OperationIndex operationIndex) {
        ShapeId tagResourceId = ShapeId.fromParts(service.getId().getNamespace(),
                                    TaggingShapeUtils.TAG_RESOURCE_OPNAME);
        if (service.getOperations().contains(tagResourceId)) {
            OperationShape tagResourceOperation = model.expectShape(tagResourceId).asOperationShape().get();
            Map<String, MemberShape> inputMembers = operationIndex.getInputMembers(tagResourceOperation);

            return inputMembers.entrySet().stream().filter(memberEntry ->
                    TaggingShapeUtils.isTagDesiredName(memberEntry.getKey())
                    && TaggingShapeUtils.verifyTagsShape(model,
                        model.expectShape(memberEntry.getValue().getTarget())))
                .count() == 1
                    && TaggingShapeUtils.hasResourceArnInput(inputMembers, model);
        }
        return false;
    }

    static boolean verifyUntagResourceOperation(Model model, ServiceShape service, OperationIndex operationIndex) {
        ShapeId untagResourceId = ShapeId.fromParts(service.getId().getNamespace(),
                                    TaggingShapeUtils.UNTAG_RESOURCE_OPNAME);
        if (service.getOperations().contains(untagResourceId)) {
            OperationShape untagResourceOperation = model.expectShape(untagResourceId).asOperationShape().get();
            Map<String, MemberShape> inputMembers = operationIndex.getInputMembers(untagResourceOperation);
            return inputMembers.entrySet().stream().filter(memberEntry ->
                    TaggingShapeUtils.isTagKeysDesiredName(memberEntry.getKey())
                    && TaggingShapeUtils.verifyTagKeysShape(model,
                        model.expectShape(memberEntry.getValue().getTarget()))
                ).count() == 1
                    && TaggingShapeUtils.hasResourceArnInput(inputMembers, model);
        }
        return false;
    }

    static boolean verifyListTagsOperation(Model model, ServiceShape service, OperationIndex operationIndex) {
        ShapeId listTagsResourceId = ShapeId.fromParts(service.getId().getNamespace(),
                                        TaggingShapeUtils.LIST_TAGS_OPNAME);
        if (service.getOperations().contains(listTagsResourceId)) {
            OperationShape listTagsResourceOperation = model.expectShape(listTagsResourceId)
                                                            .asOperationShape().get();
            Map<String, MemberShape> inputMembers = operationIndex.getInputMembers(listTagsResourceOperation);
            Map<String, MemberShape> outputMembers = operationIndex.getOutputMembers(listTagsResourceOperation);
            return outputMembers.entrySet().stream().filter(memberEntry ->
                    TaggingShapeUtils.isTagDesiredName(memberEntry.getKey())
                    && TaggingShapeUtils.verifyTagsShape(model,
                        model.expectShape(memberEntry.getValue().getTarget()))
                ).count() == 1
                    && TaggingShapeUtils.hasResourceArnInput(inputMembers, model);
        }
        return false;
    }

    static boolean isTagPropertyInInput(
        Optional<ShapeId> operationId,
        Model model,
        ResourceShape resource,
        PropertyBindingIndex propertyBindingIndex
    ) {
        Optional<String> property = resource.expectTrait(TaggableTrait.class).getProperty();
        if (property.isPresent()) {
            String tagPropertyName = property.get();
            if (operationId.isPresent()) {
                OperationShape operation = model.expectShape(operationId.get()).asOperationShape().get();
                Shape inputShape = model.expectShape(operation.getInputShape());
                return isTagPropertyInShape(tagPropertyName, inputShape, propertyBindingIndex);
            }
        }
        return false;
    }

    static boolean isTagPropertyInOutput(
        Optional<ShapeId> operationId,
        Model model,
        ResourceShape resource,
        PropertyBindingIndex propertyBindingIndex
    ) {
        Optional<String> property = resource.expectTrait(TaggableTrait.class).getProperty();
        if (property.isPresent()) {
            String tagPropertyName = property.get();
            if (operationId.isPresent()) {
                OperationShape operation = model.expectShape(operationId.get()).asOperationShape().get();
                Shape outputShape = model.expectShape(operation.getOutputShape());
                return isTagPropertyInShape(tagPropertyName, outputShape, propertyBindingIndex);
            }
        }
        return false;
    }

    static boolean isTagPropertyInShape(
        String tagPropertyName,
        Shape shape,
        PropertyBindingIndex propertyBindingIndex
    ) {
        for (MemberShape member : shape.members()) {
            Optional<Boolean> isMatch = propertyBindingIndex.getPropertyName(member.getId())
            .map(name -> name.equals(tagPropertyName));
            if (isMatch.isPresent() && isMatch.get()) {
                return true;
            }
        }
        return false;
    }
}
