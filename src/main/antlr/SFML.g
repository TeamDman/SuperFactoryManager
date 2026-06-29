grammar SFML;

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
                ;

// IO STATEMENT
inputstatement  : INPUT inputmatchers? FROM EACH? labelaccess;
outputstatement : OUTPUT outputmatchers? TO EACH? labelaccess;
inputmatchers   : itemmovement; // separate for different defaults
outputmatchers  : itemmovement; // separate for different defaults
itemmovement    : itemlimit (COMMA itemlimit)*  #ItemLimitMovement
                | limit                         #LimitMovement
                ;
itemlimit       : limit? item;
limit           : quantity retention    #QuantityRetentionLimit
                | retention             #RetentionLimit
                | quantity              #QuantityLimit
                ;

quantity        : number;
retention       : RETAIN number;

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
                | setOp? labelaccess HAS itemcomparison #BooleanHas
                | REDSTONE (comparisonOp number)?       #BooleanRedstone
                ;
itemcomparison  : comparisonOp number item ;
comparisonOp    : GT
                | LT
                | EQ
                | LE
                | GE
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
labelaccess     : label (COMMA label)* sidequalifier? slotqualifier?;
label           : IDENTIFIER ;
item            : IDENTIFIER (COLON IDENTIFIER)?        # ItemRaw
                | STRING                                # ItemString
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
NO      : N O ;

// BOOLEAN LOGIC
TRUE    : T R U E ;
FALSE   : F A L S E ;
NOT     : N O T ;
AND     : A N D ;
OR      : O R ;

// QUANTITY LOGIC
GT      : G T ;
LT      : L T ;
EQ      : E Q ;
LE      : L E ;
GE      : G E ;

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
EVERY           : E V E R Y ;

COMMA   : ',';
COLON   : ':';
DASH    : '-';
LPAREN  : '(';
RPAREN  : ')';


IDENTIFIER      : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER          : [0-9]+ ;

STRING : '"' (~'"'|'\\"')* '"' ;

LINE_COMMENT : '--' ~[\r\n]* (EOF|'\r'? '\n') -> skip ;

WS
        :   [ \r\t\n]+ -> channel(HIDDEN)
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