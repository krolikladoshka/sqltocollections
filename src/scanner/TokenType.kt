package scanner

enum class TokenType {
    Is,
    Select,
    Distinct,
    From,
    As,
    Where,
    And,
    Or,
    Not,
    In,
    Null,
    True,
    False,
    Between,
    Like,
    Ilike,
    Exists,
    All,
    Any,
    Union,
    Intersect,
    Except,
    Group,
    By,
    Having,
    Order,
    Asc,
    Desc,
    Limit,
    Offset,
    Case,
    When,
    Then,
    Else,
    End,
    Join,
    Inner,
    Left,
    Right,
    Full,
    Outer,
    On,
    Cross,

    Identifier,

    Plus,
    Minus,
    Star,
    Slash,
    Percent,
    Equals,
    NotEquals,
    Exclamation,
    Less,
    Greater,
    LessEquals,
    GreaterEquals,
    Dot,
    Comma,
    Comment,
    Number,
    String,
    LeftParenthesis,
    RightParenthesis,
    LeftSquareBracket,
    RightSquareBracket,
    Semicolon,
    EOF
}


val keywords = mapOf(
    "is" to TokenType.Is,
    "select" to TokenType.Select,
    "distinct" to TokenType.Distinct,
    "from" to TokenType.From,
    "as" to TokenType.As,
    "where" to TokenType.Where,
    "and" to TokenType.And,
    "or" to TokenType.Or,
    "not" to TokenType.Not,
    "in" to TokenType.In,
    "null" to TokenType.Null,
    "true" to TokenType.True,
    "false" to TokenType.False,
    "between" to TokenType.Between,
    "like" to TokenType.Like,
    "ilike" to TokenType.Ilike,
    "exists" to TokenType.Exists,
    "all" to TokenType.All,
    "any" to TokenType.Any,
    "union" to TokenType.Union,
    "intersect" to TokenType.Intersect,
    "except" to TokenType.Except,
    "group" to TokenType.Group,
    "by" to TokenType.By,
    "having" to TokenType.Having,
    "order" to TokenType.Order,
    "asc" to TokenType.Asc,
    "desc" to TokenType.Desc,
    "limit" to TokenType.Limit,
    "offset" to TokenType.Offset,
    "case" to TokenType.Case,
    "when" to TokenType.When,
    "then" to TokenType.Then,
    "else" to TokenType.Else,
    "end" to TokenType.End,
    "join" to TokenType.Join,
    "inner" to TokenType.Inner,
    "left" to TokenType.Left,
    "right" to TokenType.Right,
    "full" to TokenType.Full,
    "outer" to TokenType.Outer,
    "on" to TokenType.On,
    "cross" to TokenType.Cross
)
