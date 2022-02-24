$version: "2.0"

namespace smithy.example

enum EnumWithoutValueTraits {
    FOO
    BAR
    BAZ
}

enum EnumWithValueTraits {
    @enumValue("foo")
    FOO

    @enumValue("bar")
    BAR

    @enumValue("baz")
    BAZ
}

enum EnumWithDefaultBound {
    @enumDefault
    DEFAULT
}

intEnum IntEnum {
    @enumValue(1)
    FOO

    @enumValue(2)
    BAR

    @enumValue(3)
    BAZ
}

intEnum IntEnumWithDefaultBound {
    @enumDefault
    DEFAULT
}
