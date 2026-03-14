// GOALS:
// - label tag operations; INPUT FROM * WITH ID "*phyto*"
// - test binary operator precidence for sets and numeric expressions; https://stackoverflow.com/questions/64257414/antlr4-operator-precedence-changes
// - OUTPUT 5 BUNDLES OF 3 SAME *shard*, 1 stick TO EMPTY infuser
// - OUTPUT 5 BUNDLES OF 1 *seed* IN SLOT 0, 1 *ingot* TO EMPTY altar
// - OUTPUT EXACTLY 3 BUNDLES of 1 *seed*, 1 stone TO chest
// - OUTPUT 3 BUNDLES of 1 *seed*, 1 stone TO EACH chest
// -- bundles, EMPTY qualifier, IN SLOT(s) qualifier, EXACTLY, EACH chest

grammar SFML;
@header {
package ca.teamdman.langs;
}
@lexer::members {
    public boolean INCLUDE_UNUSED = false; // we want syntax highlighting to not break on unexpected tokens
}

program : name? outerExpression* EOF;

outerExpression : trigger | logExpression;

logExpression   : LOG logLevel displayable       #LogStatement
                | PRINT displayable              #PrintStatement
                | INFO displayable               #InfoStatement
                | DEBUG displayable              #DebugStatement
                | (WARN | WARNING) displayable   #WarnStatement
                | ERROR displayable              #ErrorStatement
                | TRACE displayable              #TraceStatement
                ;

displayable : string            # DisplayableString
            | numberExpression  # DisplayableNumberExpression
            ;

logLevel        : INFO | DEBUG | WARN | WARNING | ERROR | TRACE ;

name: NAME string ;

trigger : EVERY interval DO block END           #TimerTrigger
        | EVERY REDSTONE PULSE DO block END     #PulseTrigger
        ;

interval: numberExpression? GLOBAL? durationUnit (OFFSET BY numberExpression durationUnit?)?;
durationUnit: (TICKS | TICK | SECONDS | SECOND);

numberExpression  : NUMBER                                      # NumberExpressionLiteral
                  | LPAREN numberExpression RPAREN              # NumberExpressionParen
                  | numberExpression CARET numberExpression     # NumberExpressionExponential
                  | numberExpression ASTERISK numberExpression  # NumberExpressionMultiplication
                  | numberExpression SLASH numberExpression     # NumberExpressionDivision
                  | numberExpression PLUS numberExpression      # NumberExpressionAddition
                  | numberExpression DASH numberExpression      # NumberExpressionSubtraction
                  | numberExpression PERCENT numberExpression   # NumberExpressionModulus
                  ;

block           : statement*;
statement       : inputStatement
                | outputStatement
                | ifStatement
                | forgetStatement
                | logExpression
                ;

inputStatement  : INPUT inputResourceLimits? resourceExclusion? FROM EACH? resourceAccess
                | FROM EACH? resourceAccess INPUT inputResourceLimits? resourceExclusion?
                ;
outputStatement : OUTPUT outputResourceLimits? resourceExclusion? TO emptyslots? EACH? resourceAccess
                | TO emptyslots? EACH? resourceAccess OUTPUT outputResourceLimits? resourceExclusion?
                ;

forgetStatement : FORGET labelExpression? (COMMA labelExpression)* COMMA?; // NEEDS TEST

label           : (identifier)  #RawLabel
                | string        #StringLabel
                ;

inputResourceLimits   : resourceLimitList; // separate for different defaults
outputResourceLimits  : resourceLimitList; // separate for different defaults

resourceLimitList   : resourceLimit (COMMA resourceLimit)* COMMA?;
resourceLimit       : limit? resourceIdDisjunction with?
                    | limit with?
                    | with
                    ;

limit   : quantity retention    #QuantityRetentionLimit
        | retention             #RetentionLimit
        | quantity              #QuantityLimit
        ;

quantity        : numberExpression EACH?;
retention       : RETAIN numberExpression EACH?;

resourceExclusion : EXCEPT resourceIdList;

resourceId  : (identifier) (COLON (identifier)? (COLON (identifier)? (COLON (identifier)?)?)?)? # Resource
            | string                                                                            # StringResource
            ;

resourceIdList          : resourceId (COMMA resourceId)* COMMA?;
resourceIdDisjunction   : resourceId (OR resourceId)* OR?;


with    : WITH withClause
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

ifStatement     : IF boolExpr THEN block (ELSE IF boolExpr THEN block)* (ELSE block)? END;
boolExpr        : TRUE                              #BooleanTrue
                | FALSE                             #BooleanFalse
                | LPAREN boolExpr RPAREN            #BooleanParen
                | NOT boolExpr                      #BooleanNegation
                | boolExpr AND boolExpr             #BooleanConjunction
                | boolExpr OR boolExpr              #BooleanDisjunction
                | numberExpression comparisonOp numberExpression  #BooleanComparison
                | setOp? resourceAccess HAS comparisonOp numberExpression resourceIdDisjunction? with? (EXCEPT resourceIdList)?  #BooleanHas
                | REDSTONE (comparisonOp numberExpression)?   #BooleanRedstone
                ;

comparisonOp    : GT
                | LT
                | EQ
                | NE
                | LE
                | GE
                | GT_SYMBOL
                | LT_SYMBOL
                | EQ_SYMBOL
                | NE_SYMBOL
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

resourceAccess : labelExpression (COMMA labelExpression)* roundrobin? sideQualifier? slotQualifier?;

labelExpression : label                                      # LabelExpressionSingle          // NEEDS TEST
                | LPAREN labelExpression RPAREN              # LabelExpressionParen           // NEEDS TEST
                | labelExpression EXCEPT    labelExpression  # LabelExpressionExclusion       // NEEDS TEST
                | labelExpression INTERSECT labelExpression  # LabelExpressionIntersection    // NEEDS TEST
                | labelExpression UNION     labelExpression  # LabelExpressionUnion           // NEEDS TEST
                ;

roundrobin : ROUND ROBIN BY (LABEL | BLOCK);

sideQualifier   : ALL (SIDE|SIDES)                      #AllSides     // NEEDS TEST
                | EACH? side (COMMA side)* (SIDE|SIDES) #ListedSides  // NEEDS TEST
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

slotQualifier   : EACH? (SLOTS | SLOT) numberSet;                   // NEEDS TEST
numberSet       : numberRange (COMMA numberRange)*;
numberRange     : NOT? numberExpression (TO numberExpression)? ;  // NEEDS TEST


emptyslots      : EMPTY (SLOTS | SLOT) IN ;

identifier : (
    IDENTIFIER
    | REDSTONE
    | GLOBAL
    | SECOND
    | SECONDS
    | TOP
    | BOTTOM
    | LEFT
    | RIGHT
    | FRONT
    | BACK
    | LOG
    | BLOCK
    | LABEL
    | ASTERISK
)+ ;

// GENERAL
string: STRING ;



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
NE        : N E ;
NE_SYMBOL : '!=' | '<>' ;
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
SLOT    : S L O T ;
RETAIN  : R E T A I N ;
EACH    : E A C H ;
ALL     : A N Y ;
EXCEPT  : E X C E P T ;
INTERSECT  : I N T E R S E C T ;
UNION   : U N I O N ;
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
SIDES   : S I D E S ;
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
GLOBAL  : G L O B A L;
OFFSET : O F F S E T ;

// REDSTONE TRIGGER
REDSTONE        : R E D S T O N E ;
PULSE           : P U L S E;

// PROGRAM SYMBOLS
DO              : D O ;
END             : E N D ;
NAME            : N A M E ;

// LOGGING
LOG             : L O G ;
PRINT           : P R I N T ;
INFO            : I N F O ;
DEBUG           : D E B U G ;
WARN            : W A R N ;
WARNING         : W A R N I N G ;
ERROR           : E R R O R ;
TRACE           : T R A C E ;

// GENERAL SYMBOLS
// used by triggers and as a set operator
EVERY           : E V E R Y ;

COMMA   : ',';
COLON   : ':';
SLASH   : '/';
DASH    : '-';
PERCENT : '%';
CARET   : '^';
PLUS    : '+';
LPAREN  : '(';
RPAREN  : ')';
ASTERISK: '*';

NUMBER                  : [0-9][0-9_]* ;

IDENTIFIER              : [a-zA-Z_][a-zA-Z0-9_]*;
// Note that a * in the square brackets means a literal
// We have moved this to be the ASTERISK token instead of including it here; the `identifier` rule has been updated.

STRING : '"' (~'"'|'\\"')* '"' ;

LINE_COMMENT : '--' ~[\r\n]* -> channel(HIDDEN);
//LINE_COMMENT : '--' ~[\r\n]* (EOF|'\r'? '\n');

WS
        :   [ \r\t\n]+ -> channel(HIDDEN)
        ;

UNUSED
        :   {INCLUDE_UNUSED}? . -> channel(HIDDEN)
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