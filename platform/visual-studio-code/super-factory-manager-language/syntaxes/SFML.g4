grammar SFML;

//Need to get rip of the @header when copying

//go to SFMLLexer.ts after going antlr command on the package.json and solve the 2 issues
@lexer::members {
    public INCLUDE_UNUSED: boolean = false; // we want syntax highlighting to not break on unexpected tokens
}

program : name? trigger* EOF;

name: NAME string ;

//
// TRIGGERS
//

trigger : EVERY interval DO block END           #TimerTrigger
        | EVERY REDSTONE PULSE DO block END     #PulseTrigger
        ;

interval: NUMBER? GLOBAL? (PLUS NUMBER)? (TICKS | TICK | SECONDS | SECOND)      # IntervalSpace
        | NUMBER_WITH_G_SUFFIX (PLUS NUMBER)? (TICKS | TICK | SECONDS | SECOND) # IntervalNoSpace;

//
// BLOCK STATEMENT
//

block           : statement* ;
statement       : inputStatement
                | outputStatement
                | ifStatement
                | forgetStatement
                ;

// IO STATEMENT
forgetStatement : FORGET label? (COMMA label)* COMMA?;
inputStatement  : INPUT inputResourceLimits? resourceExclusion? FROM EACH? labelAccess
                | FROM EACH? labelAccess INPUT inputResourceLimits? resourceExclusion?
                ;
outputStatement : OUTPUT outputResourceLimits? resourceExclusion? TO emptyslots? EACH? labelAccess
                | TO emptyslots? EACH? labelAccess OUTPUT outputResourceLimits? resourceExclusion?
                ;

inputResourceLimits   : resourceLimitList; // separate for different defaults
outputResourceLimits  : resourceLimitList; // separate for different defaults

resourceLimitList  : resourceLimit (COMMA resourceLimit)* COMMA?;
resourceLimit   : limit? resourceIdDisjunction with?
                | limit with?
                | with
                ;
limit           : quantity retention    #QuantityRetentionLimit
                | retention             #RetentionLimit
                | quantity              #QuantityLimit
                ;

quantity        : number EACH?;
retention       : RETAIN number EACH?;

resourceExclusion       : EXCEPT resourceIdList;

resourceId      : (identifier) (COLON (identifier)? (COLON (identifier)? (COLON (identifier)?)?)?)? # Resource
                | string                                                                            # StringResource
                ;

resourceIdList          : resourceId (COMMA resourceId)* COMMA?;
resourceIdDisjunction   : resourceId (OR resourceId)* OR?;


with        : WITH withClause
            | WITHOUT withClause
            ;
withClause  : LPAREN withClause RPAREN           # WithParen
            | NOT withClause                     # WithNegation
            | withClause AND withClause          # WithConjunction
            | withClause OR withClause           # WithDisjunction
            | (TAG HASHTAG?|HASHTAG) tagMatcher  # WithTag
            ;

tagMatcher  : identifier COLON identifier (SLASH identifier)*
            | identifier (SLASH identifier)*
            ;


sidequalifier   : EACH SIDE                  #EachSide
                | side (COMMA side)* SIDE    #ListedSides
                ;

side            : TOP
                | BOTTOM
                | NORTH
                | EAST
                | SOUTH
                | WEST
                | LEFT
                | RIGHT
                | FRONT
                | BACK
                | NULL
                ;

slotqualifier   : (SLOTS | SLOT) rangeset;
rangeset        : range (COMMA range)*;
range           : number (DASH number)? ;


ifStatement     : IF boolexpr THEN block (ELSE IF boolexpr THEN block)* (ELSE block)? END;
boolexpr        : TRUE                              #BooleanTrue
                | FALSE                             #BooleanFalse
                | LPAREN boolexpr RPAREN            #BooleanParen
                | NOT boolexpr                      #BooleanNegation
                | boolexpr AND boolexpr             #BooleanConjunction
                | boolexpr OR boolexpr              #BooleanDisjunction
                | setOp? labelAccess HAS comparisonOp number resourceIdDisjunction? with? (EXCEPT resourceIdList)?  #BooleanHas
                | REDSTONE (comparisonOp number)?   #BooleanRedstone
                ;

comparisonOp    : GT
                | LT
                | EQ
                | LE
                | GE
                | GT_SYMBOL
                | LT_SYMBOL
                | EQ_SYMBOL
                | LE_SYMBOL
                | GE_SYMBOL
                ;
setOp           : OVERALL
                | SOME
                | EVERY
                | EACH
                | ONE
                | LONE
                ;





//
// IO HELPERS
//
labelAccess     : label (COMMA label)* roundrobin? sidequalifier? slotqualifier?;
roundrobin      : ROUND ROBIN BY (LABEL | BLOCK);

label           : (identifier)  #RawLabel
                | string        #StringLabel
                ;

emptyslots      : EMPTY (SLOTS | SLOT) IN ;

identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK) ;

// GENERAL
string: STRING ;
number: NUMBER ;



//
// LEXER
//

// IF STATEMENT
IF      : I F ;
THEN    : T H E N ;
ELSE    : E L S E ;

HAS     : H A S ;
OVERALL : O V E R A L L ;
SOME    : S O M E ;
ONE     : O N E ;
LONE    : L O N E ;

// BOOLEAN LOGIC
TRUE    : T R U E ;
FALSE   : F A L S E ;
NOT     : N O T ;
AND     : A N D ;
OR      : O R ;

// QUANTITY LOGIC
GT        : G T ;
GT_SYMBOL : '>' ;
LT        : L T ;
LT_SYMBOL : '<' ;
EQ        : E Q ;
EQ_SYMBOL : '=' ;
LE        : L E ;
LE_SYMBOL : '<=' ;
GE        : G E ;
GE_SYMBOL : '>=' ;

// IO LOGIC
FROM    : F R O M ;
TO      : T O ;
INPUT   : I N P U T ;
OUTPUT  : O U T P U T ;
WHERE   : W H E R E ;
SLOTS   : S L O T S ;
SLOT   : S L O T ;
RETAIN  : R E T A I N ;
EACH    : E A C H ;
EXCEPT  : E X C E P T ;
FORGET  : F O R G E T ;
EMPTY   : E M P T Y ;
IN      : I N ;

// WITH LOGIC
WITHOUT : W I T H O U T;
WITH    : W I T H ;
TAG     : T A G ;
HASHTAG : '#' ;

// ROUND ROBIN
ROUND : R O U N D ;
ROBIN : R O B I N ;
BY    : B Y ;
LABEL : L A B E L ;
BLOCK : B L O C K ;

// SIDE LOGIC
TOP     : T O P ;
BOTTOM  : B O T T O M ;
NORTH   : N O R T H ;
EAST    : E A S T ;
SOUTH   : S O U T H ;
WEST    : W E S T ;
SIDE    : S I D E ;
LEFT    : L E F T ;
RIGHT   : R I G H T ;
FRONT   : F R O N T ;
BACK    : B A C K ;
NULL    : N U L L ;


// TIMER TRIGGERS
TICKS   : T I C K S ;
TICK    : T I C K ;
SECONDS : S E C O N D S ;
SECOND  : S E C O N D ;
GLOBAL  : (G L O B A L) | G;
PLUS    : '+' | P L U S;

// REDSTONE TRIGGER
REDSTONE        : R E D S T O N E ;
PULSE           : P U L S E;

// PROGRAM SYMBOLS
DO              : D O ;
END             : E N D ;
NAME            : N A M E ;

// GENERAL SYMBOLS
// used by triggers and as a set operator
EVERY           : E V E R Y ;

COMMA   : ',';
COLON   : ':';
SLASH   : '/';
DASH    : '-';
LPAREN  : '(';
RPAREN  : ')';


NUMBER_WITH_G_SUFFIX    : [0-9]+[gG] ;
NUMBER                  : [0-9]+ ;
IDENTIFIER              : [a-zA-Z_*][a-zA-Z0-9_*]* | '*'; // Note that the * in the square brackets is a literl

STRING : '"' (~'"'|'\\"')* '"' ;

LINE_COMMENT : '--' ~[\r\n]* -> channel(HIDDEN);
//LINE_COMMENT : '--' ~[\r\n]* (EOF|'\r'? '\n');

WS
        :   [ \r\t\n]+ -> channel(HIDDEN)
        ;

UNUSED
        :   {this.INCLUDE_UNUSED}? . -> channel(HIDDEN)
        ;

fragment A  :('a' | 'A') ;
fragment B  :('b' | 'B') ;
fragment C  :('c' | 'C') ;
fragment D  :('d' | 'D') ;
fragment E  :('e' | 'E') ;
fragment F  :('f' | 'F') ;
fragment G  :('g' | 'G') ;
fragment H  :('h' | 'H') ;
fragment I  :('i' | 'I') ;
fragment J  :('j' | 'J') ;
fragment K  :('k' | 'K') ;
fragment L  :('l' | 'L') ;
fragment M  :('m' | 'M') ;
fragment N  :('n' | 'N') ;
fragment O  :('o' | 'O') ;
fragment P  :('p' | 'P') ;
fragment Q  :('q' | 'Q') ;
fragment R  :('r' | 'R') ;
fragment S  :('s' | 'S') ;
fragment T  :('t' | 'T') ;
fragment U  :('u' | 'U') ;
fragment V  :('v' | 'V') ;
fragment W  :('w' | 'W') ;
fragment X  :('x' | 'X') ;
fragment Y  :('y' | 'Y') ;
fragment Z  :('z' | 'Z') ;