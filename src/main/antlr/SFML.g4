grammar SFML;
@lexer::members {
    public boolean INCLUDE_UNUSED = false; // we want syntax highlighting to not break on unexpected tokens
}

program : name? trigger* EOF;

name: NAME string ;

//
// TRIGGERS
//

trigger : EVERY interval DO block END           #TimerTrigger
        | EVERY REDSTONE PULSE DO block END     #PulseTrigger
        ;

interval: number TICKS     #Ticks
        | number SECONDS   #Seconds
        ;

//
// BLOCK STATEMENT
//

block           : statement* ;
statement       : inputstatement    #InputStatementStatement
                | outputstatement   #OutputStatementStatement
                | ifstatement       #IfStatementStatement
                | forgetstatement   #ForgetStatementStatement
                ;

// IO STATEMENT
forgetstatement : FORGET label (COMMA label)* COMMA?;
inputstatement  : INPUT inputmatchers? resourceexclusion? FROM EACH? labelaccess
                | FROM EACH? labelaccess INPUT inputmatchers? resourceexclusion?
                ;
outputstatement : OUTPUT outputmatchers? resourceexclusion? TO EACH? labelaccess
                | TO EACH? labelaccess OUTPUT outputmatchers? resourceexclusion?
                ;
inputmatchers   : movement; // separate for different defaults
outputmatchers  : movement; // separate for different defaults

movement        : resourcelimit (COMMA resourcelimit)* COMMA?    #ResourceLimitMovement
                | limit                                          #LimitMovement
                ;

resourceexclusion       : EXCEPT resourceid (COMMA resourceid)* COMMA?;

resourcelimit   : limit? resourceid;

limit           : quantity retention    #QuantityRetentionLimit
                | retention             #RetentionLimit
                | quantity              #QuantityLimit
                ;

quantity        : number EACH?;
retention       : RETAIN number EACH?;

sidequalifier   : side(COMMA side)* SIDE;
side            : TOP
                | BOTTOM
                | NORTH
                | EAST
                | SOUTH
                | WEST
                ;
slotqualifier   : SLOTS rangeset;
rangeset        : range (COMMA range)*;
range           : number (DASH number)? ;


ifstatement     : IF boolexpr THEN block (ELSE IF boolexpr THEN block)* (ELSE block)? END;
boolexpr        : TRUE                                  #BooleanTrue
                | FALSE                                 #BooleanFalse
                | LPAREN boolexpr RPAREN                #BooleanParen
                | NOT boolexpr                          #BooleanNegation
                | boolexpr AND boolexpr                 #BooleanConjunction
                | boolexpr OR boolexpr                  #BooleanDisjunction
                | setOp? labelaccess HAS resourcecomparison #BooleanHas
                | REDSTONE (comparisonOp number)?       #BooleanRedstone
                ;
resourcecomparison : comparisonOp number resourceid ;
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
                | ONE
                | LONE
                ;






//
// IO HELPERS
//
labelaccess     : label (COMMA label)* roundrobin? sidequalifier? slotqualifier?;
roundrobin: ROUND ROBIN BY (LABEL | BLOCK);
label           : IDENTIFIER #RawLabel
                | string    #StringLabel
                ;

resourceid      : IDENTIFIER (COLON IDENTIFIER? (COLON IDENTIFIER? (COLON IDENTIFIER?)?)?)? # Resource
                | string                             # StringResource
                ;

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
MOVE    : M O V E ;
FROM    : F R O M ;
TO      : T O ;
INPUT   : I N P U T ;
OUTPUT  : O U T P U T ;
WHERE   : W H E R E ;
SLOTS   : S L O T S ;
RETAIN  : R E T A I N ;
EACH    : E A C H ;
EXCEPT  : E X C E P T ;
FORGET  : F O R G E T ;

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


// TRIGGERS
TICKS           : T I C K S ;
SECONDS         : S E C O N D S ;
// REDSTONE TRIGGER
REDSTONE        : R E D S T O N E ;
PULSE           : P U L S E;
// PROGRAM SYMBOLS
DO              : D O ;
WORLD           : W O R L D ;
PROGRAM         : P R O G R A M ;
END             : E N D ;
NAME            : N A M E ;

// GENERAL SYMBOLS
// used by triggers and as a set operator
EVERY           : E V E R Y ;

COMMA   : ',';
COLON   : ':';
DASH    : '-';
LPAREN  : '(';
RPAREN  : ')';


IDENTIFIER      : [a-zA-Z_*][a-zA-Z0-9_*]* | '*';
NUMBER          : [0-9]+ ;

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