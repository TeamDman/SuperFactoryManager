grammar SFML;
@header {
package ca.teamdman.langs;
}
@lexer::members {
    public boolean INCLUDE_UNUSED = false; // we want syntax highlighting to not break on unexpected tokens
}

{% if features.sfml_execution_side %}
{% if features.packet_computation %}
program : executionSideDeclaration? name? declaration* trigger* EOF;
{% else %}
program : executionSideDeclaration? name? trigger* EOF;
{% endif %}
{% elsif features.packet_computation %}
program : name? declaration* trigger* EOF;
{% else %}
program : name? trigger* EOF;
{% endif %}
{% if features.sfml_execution_side %}

executionSideDeclaration : (CLIENT | SERVER) BTW;
{% endif %}

name: NAME string ;

{% if features.packet_computation %}
declaration : LET identifier BE PLAYER OF identifier #PlayerDeclaration
            | LET identifier BE LIKE valuePattern     #PatternDeclaration
            ;

valuePattern : GUID                                                   #GuidValuePattern
             | STRING_TYPE                                            #StringValuePattern
             | string                                                 #LiteralValuePattern
             | OBJECT WITH FIELD patternField (AND FIELD patternField)* #ObjectValuePattern
             | identifier                                             #AliasValuePattern
             ;

patternField : identifier OF string          #LiteralPatternField
             | identifier LIKE identifier    #LikePatternField
             | identifier                    #AliasPatternField
             ;

{% endif %}
//
// TRIGGERS
//

trigger : EVERY interval DO block END           #TimerTrigger
        | EVERY REDSTONE PULSE DO block END     #PulseTrigger
{% if features.client_frame_language %}
        | EVERY FRAME FOR frameLabels AS identifier DO block END #FrameTrigger
{% endif %}
        ;

{% if features.client_frame_language %}
frameLabels : label (COMMA label)*;

{% endif %}
{% if features.sfml_worded_intervals %}
interval: period=NUMBER? GLOBAL? (PLUS legacyOffset=NUMBER)? unit=timeUnit (OFFSET BY newOffset=NUMBER offsetUnit=timeUnit)?      # IntervalSpace
        | period=NUMBER_WITH_G_SUFFIX (PLUS legacyOffset=NUMBER)? unit=timeUnit (OFFSET BY newOffset=NUMBER offsetUnit=timeUnit)? # IntervalNoSpace;
timeUnit: TICKS | TICK | SECONDS | SECOND;
{% else %}
interval: NUMBER? GLOBAL? (PLUS NUMBER)? (TICKS | TICK | SECONDS | SECOND)      # IntervalSpace
        | NUMBER_WITH_G_SUFFIX (PLUS NUMBER)? (TICKS | TICK | SECONDS | SECOND) # IntervalNoSpace;
{% endif %}

//
// BLOCK STATEMENT
//

block           : statement* ;
statement       : inputStatement
                | outputStatement
                | ifStatement
                | forgetStatement
{% if features.packet_computation %}
                | letValueStatement
                | createStatement
{% endif %}
{% if features.packet_transport_private %}
                | broadcastStatement
{% endif %}
{% if features.client_frame_render %}
                | renderImageStatement
{% endif %}
                ;

{% if features.client_frame_render %}
renderImageStatement : RENDER IMAGE string TO identifier;

{% endif %}
{% if features.packet_computation %}
letValueStatement : LET identifier BE valueExpression;
valueExpression : STRING_TYPE OF INVOKE invokeActionId WITH identifier                      #InvokeTextValueExpression
                | identifier WITH FIELD constructionField (AND FIELD constructionField)*    #ObjectConstructionValueExpression
{% if features.client_program_actions %}
                | JSON string                                                               #ClientJsonValueExpression
                | INVOKE invokeActionId WITH identifier                                      #ClientInvokeValueExpression
                | FIELD string OF identifier                                                 #ClientFieldValueExpression
{% endif %}
                ;
constructionField : identifier OF fieldValueExpression;
fieldValueExpression : NEW GUID #NewGuidFieldValue
                     | string   #LiteralFieldValue
                     | identifier #VariableFieldValue
                     ;
createStatement : CREATE INPUT qualifiedId WITH identifier;
{% endif %}
{% if features.packet_transport_private %}
{% if features.client_inbox %}
broadcastStatement : BROADCAST TO identifier (CHANNEL qualifiedId)?;
{% else %}
broadcastStatement : BROADCAST TO identifier;
{% endif %}
{% endif %}
{% if features.packet_computation %}

{% elsif features.packet_transport_private %}

{% elsif features.client_frame_render %}

{% endif %}
// IO STATEMENT
forgetStatement : FORGET label? (COMMA label)* COMMA?;
{% if features.packet_computation %}
inputStatement  : INPUT inputSelection? inputResourceLimits? resourceExclusion? FROM EACH? labelAccess inputBinding?
                | FROM EACH? labelAccess INPUT inputSelection? inputResourceLimits? resourceExclusion? inputBinding?
{% else %}
inputStatement  : INPUT inputResourceLimits? resourceExclusion? FROM EACH? labelAccess
                | FROM EACH? labelAccess INPUT inputResourceLimits? resourceExclusion?
{% endif %}
                ;
{% if features.packet_computation %}
inputSelection  : WITH CAPABILITY qualifiedId #CapabilityInputSelection
                | LIKE identifier             #PatternInputSelection
                ;
inputBinding    : AS identifier;
{% endif %}
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

{% if features.packet_computation %}
qualifiedId : identifier COLON identifier (SLASH identifier)*;
// Action IDs are static literals; quotes allow resource paths containing keywords or punctuation.
invokeActionId : qualifiedId | string;


{% else %}

{% endif %}
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
{% if features.client_frame_language %}
                | FRAME MOD number comparisonOp number #BooleanFrameModulo
{% endif %}
{% if features.client_program_actions %}
                | identifier (EQ | EQ_SYMBOL) JSON string #BooleanClientValueEquals
{% endif %}
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

{% if features.packet_computation %}
identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
{% elsif features.sfml_execution_side %}
identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
{% elsif features.sfml_worded_intervals %}
identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
{% elsif features.client_frame_language %}
identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
{% else %}
identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK) ;
{% endif %}
{% if features.packet_computation %}
           | LET
{% endif %}
{% if features.packet_computation %}
           | BE
{% endif %}
{% if features.packet_computation %}
           | PLAYER
{% endif %}
{% if features.packet_computation %}
           | OF
{% endif %}
{% if features.packet_computation %}
           | LIKE
{% endif %}
{% if features.packet_computation %}
           | OBJECT
{% endif %}
{% if features.packet_computation %}
           | FIELD
{% endif %}
{% if features.packet_computation %}
           | GUID
{% endif %}
{% if features.packet_computation %}
           | STRING_TYPE
{% endif %}
{% if features.packet_computation %}
           | INVOKE
{% endif %}
{% if features.packet_computation %}
           | CAPABILITY
{% endif %}
{% if features.packet_computation %}
           | AS
{% elsif features.client_frame_language %}
           | AS
{% endif %}
{% if features.packet_computation %}
           | CREATE
{% endif %}
{% if features.packet_transport_private %}
           | BROADCAST
{% endif %}
{% if features.client_inbox %}
           | CHANNEL
{% endif %}
{% if features.packet_computation %}
           | NEW
{% endif %}
{% if features.sfml_execution_side %}
           | CLIENT
{% endif %}
{% if features.sfml_execution_side %}
           | SERVER
{% endif %}
{% if features.sfml_execution_side %}
           | BTW
{% endif %}
{% if features.sfml_worded_intervals %}
           | OFFSET
{% endif %}
{% if features.client_frame_language %}
           | FRAME
{% endif %}
{% if features.client_frame_language %}
           | FOR
{% endif %}
{% if features.client_frame_language %}
           | MOD
{% endif %}
{% if features.client_frame_render %}
           | RENDER
{% endif %}
{% if features.client_frame_render %}
           | IMAGE
{% endif %}
{% if features.client_program_actions %}
           | JSON
{% endif %}
{% if features.packet_computation %}
           ) ;
{% elsif features.sfml_execution_side %}
           ) ;
{% elsif features.sfml_worded_intervals %}
           ) ;
{% elsif features.client_frame_language %}
           ) ;
{% endif %}

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
{% if features.sfml_worded_intervals %}
OFFSET  : O F F S E T;
{% endif %}
{% if features.client_frame_language %}
FRAME   : F R A M E ;
FOR     : F O R ;
MOD     : M O D ;
{% endif %}
{% if features.client_frame_render %}
RENDER  : R E N D E R ;
IMAGE   : I M A G E ;
{% endif %}

// REDSTONE TRIGGER
REDSTONE        : R E D S T O N E ;
PULSE           : P U L S E;

// PROGRAM SYMBOLS
DO              : D O ;
END             : E N D ;
NAME            : N A M E ;
{% if features.packet_computation %}
LET             : L E T ;
{% endif %}
{% if features.packet_computation %}
BE              : B E ;
{% endif %}
{% if features.packet_computation %}
PLAYER          : P L A Y E R ;
{% endif %}
{% if features.packet_computation %}
OF              : O F ;
{% endif %}
{% if features.packet_computation %}
LIKE            : L I K E ;
{% endif %}
{% if features.packet_computation %}
OBJECT          : O B J E C T ;
{% endif %}
{% if features.packet_computation %}
FIELD           : F I E L D ;
{% endif %}
{% if features.packet_computation %}
GUID            : G U I D ;
{% endif %}
{% if features.packet_computation %}
STRING_TYPE     : S T R I N G ;
{% endif %}
{% if features.packet_computation %}
INVOKE          : I N V O K E ;
{% endif %}
{% if features.packet_computation %}
CAPABILITY      : C A P A B I L I T Y ;
{% endif %}
{% if features.packet_computation %}
AS              : A S ;
{% elsif features.client_frame_language %}
AS              : A S ;
{% endif %}
{% if features.packet_computation %}
CREATE          : C R E A T E ;
{% endif %}
{% if features.packet_transport_private %}
BROADCAST       : B R O A D C A S T ;
{% endif %}
{% if features.client_inbox %}
CHANNEL         : C H A N N E L ;
{% endif %}
{% if features.packet_computation %}
NEW             : N E W ;
{% endif %}
{% if features.sfml_execution_side %}
CLIENT          : C L I E N T ;
{% endif %}
{% if features.sfml_execution_side %}
SERVER          : S E R V E R ;
{% endif %}
{% if features.sfml_execution_side %}
BTW             : B T W ;
{% endif %}
{% if features.client_program_actions %}
JSON            : J S O N ;
{% endif %}

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
