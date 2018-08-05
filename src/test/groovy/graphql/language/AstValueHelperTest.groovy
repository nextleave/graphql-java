package graphql.language

import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification

import static AstValueHelper.astFromValue
import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLID
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class AstValueHelperTest extends Specification {

    def 'converts boolean values to ASTs'() {
        expect:
        astFromValue(true, GraphQLBoolean).isEqualTo(new BooleanValue(true))

        astFromValue(false, GraphQLBoolean).isEqualTo(new BooleanValue(false))

        astFromValue(null, GraphQLBoolean) == null

        astFromValue(0, GraphQLBoolean).isEqualTo(new BooleanValue(false))

        astFromValue(1, GraphQLBoolean).isEqualTo(new BooleanValue(true))

        def NonNullBoolean = nonNull(GraphQLBoolean)
        astFromValue(0, NonNullBoolean).isEqualTo(new BooleanValue(false))
    }

    BigInteger bigInt(int i) {
        return new BigInteger(String.valueOf(i))
    }

    def 'converts Int values to Int ASTs'() {
        expect:
        astFromValue(123.0, GraphQLInt).isEqualTo(new IntValue(bigInt(123)))

        astFromValue(1e4, GraphQLInt).isEqualTo(new IntValue(bigInt(10000)))
    }

    def 'converts Float values to Int/Float ASTs'() {
        expect:
        astFromValue(123.0, GraphQLFloat).isEqualTo(new FloatValue(123.0))

        astFromValue(123.5, GraphQLFloat).isEqualTo(new FloatValue(123.5))

        astFromValue(1e4, GraphQLFloat).isEqualTo(new FloatValue(10000.0))

        astFromValue(1e40, GraphQLFloat).isEqualTo(new FloatValue(1.0e40))
    }


    def 'converts String values to String ASTs'() {
        expect:
        astFromValue('hello', GraphQLString).isEqualTo(new StringValue('hello'))

        astFromValue('VALUE', GraphQLString).isEqualTo(new StringValue('VALUE'))

        astFromValue('VA\n\t\f\r\b\\LUE', GraphQLString).isEqualTo(new StringValue('VA\\n\\t\\f\\r\\b\\\\LUE'))

        astFromValue('VA/LUE', GraphQLString).isEqualTo(new StringValue('VA/LUE'))

        astFromValue('VA\\L\"UE', GraphQLString).isEqualTo(new StringValue('VA\\\\L\\"UE'))

        astFromValue(123, GraphQLString).isEqualTo(new StringValue('123'))

        astFromValue(false, GraphQLString).isEqualTo(new StringValue('false'))

        astFromValue(null, GraphQLString) == null
    }

    def 'converts ID values to Int/String ASTs'() {
        expect:
        astFromValue('hello', GraphQLID).isEqualTo(new StringValue('hello'))

        astFromValue('VALUE', GraphQLID).isEqualTo(new StringValue('VALUE'))

        // Note: EnumValues cannot contain non-identifier characters
        astFromValue('VA\nLUE', GraphQLID).isEqualTo(new StringValue('VA\\nLUE'))

        // Note: IntValues are used when possible.
        astFromValue(123, GraphQLID).isEqualTo(new IntValue(bigInt(123)))

        astFromValue(null, GraphQLID) == null
    }


    def 'does not converts NonNull values to NullValue'() {
        expect:
        def NonNullBoolean = nonNull(GraphQLBoolean)
        astFromValue(null, NonNullBoolean) == null
    }

    def complexValue = { someArbitrary: 'complexValue' }


    def myEnum = GraphQLEnumType.newEnum().name('MyEnum')
            .value('HELLO')
            .value('GOODBYE')
            .value('COMPLEX', complexValue)
            .build()

    def 'converts string values to Enum ASTs if possible'() {
        expect:
        astFromValue('HELLO', myEnum).isEqualTo(new EnumValue('HELLO'))

        astFromValue(complexValue, myEnum).isEqualTo(new EnumValue('COMPLEX'))

//        // Note: case sensitive
//        astFromValue('hello', myEnum) == null
//
//        // Note: Not a valid enum value
//        astFromValue('VALUE', myEnum) == null
    }

    def 'converts array values to List ASTs'() {
        expect:
        astFromValue(['FOO', 'BAR'], list(GraphQLString)).isEqualTo(
                new ArrayValue([new StringValue('FOO'), new StringValue('BAR')])
        )


        astFromValue(['HELLO', 'GOODBYE'], list(myEnum)).isEqualTo(
                new ArrayValue([new EnumValue('HELLO'), new EnumValue('GOODBYE')])
        )
    }

    def 'converts list singletons'() {
        expect:
        astFromValue('FOO', list(GraphQLString)).isEqualTo(
                new StringValue('FOO')
        )
    }

    def 'converts input objects'() {
        expect:
        def inputObj = GraphQLInputObjectType.newInputObject()
                .name('MyInputObj')
                .field({ f -> f.name("foo").type(GraphQLFloat) })
                .field({ f -> f.name("bar").type(myEnum) })
                .build()

        astFromValue([foo: 3, bar: 'HELLO'], inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", new IntValue(bigInt(3))),
                                 new ObjectField("bar", new EnumValue('HELLO')),
                ])
        )
    }

    def 'converts input objects with explicit nulls'() {
        expect:
        def inputObj = GraphQLInputObjectType.newInputObject()
                .name('MyInputObj')
                .field({ f -> f.name("foo").type(GraphQLFloat) })
                .field({ f -> f.name("bar").type(myEnum) })
                .build()

        astFromValue([foo: null], inputObj).isEqualTo(
                new ObjectValue([new ObjectField("foo", null)])
        )
    }

    def 'parse ast literals'() {
        expect:
        AstValueHelper.valueFromAst(valueLiteral) in expectedValue

        where:
        valueLiteral                                  | expectedValue
        '"s"'                                         | StringValue.class
        'true'                                        | BooleanValue.class
        '666'                                         | IntValue.class
        '666.6'                                       | FloatValue.class
        '["A", "B", "C"]'                             | ArrayValue.class
        '{string : "s", integer : 1, boolean : true}' | ObjectValue.class
    }

    def "1105 - encoding of json strings"() {

        when:
        def json = AstValueHelper.jsonStringify(strValue)

        then:
        json == expected

        where:
        strValue                                  | expected
        ''                                        | ''
        'json'                                    | 'json'
        'quotation-"'                             | 'quotation-\\"'
        'reverse-solidus-\\'                      | 'reverse-solidus-\\\\'
        'solidus-/'                               | 'solidus-\\/'
        'backspace-\b'                            | 'backspace-\\b'
        'formfeed-\f'                             | 'formfeed-\\f'
        'newline-\n'                              | 'newline-\\n'
        'carriage-return-\r'                      | 'carriage-return-\\r'
        'horizontal-tab-\t'                       | 'horizontal-tab-\\t'

        // this is some AST from issue 1105
        '''"{"operator":"eq", "operands": []}"''' | '''\\"{\\"operator\\":\\"eq\\", \\"operands\\": []}\\"'''
    }
}
