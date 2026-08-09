package ca.teamdman.sfml.ast;

import ca.teamdman.langs.SFMLBaseVisitor;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import ca.teamdman.sfml.program_builder.LibraryResolver;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;

public class ASTBuilder extends SFMLBaseVisitor<ASTNode> {
    /// Used for linting and for label gun pull behaviour
    private final Set<Label> USED_LABELS = new HashSet<>();

    /// Used for linting and for energy-specific timer minimum interval restrictions
    private final Set<ResourceIdentifier<?, ?, ?>> USED_RESOURCES = new HashSet<>();

    /// Used for program editor context actions; ctrl+space on a token
    private final List<Pair<WeakReference<ASTNode>, ParserRuleContext>> AST_NODE_CONTEXTS = new LinkedList<>();

    /// Struct definitions indexed by name, populated during AST building
    private final Map<String, StructDefinition> STRUCT_DEFINITIONS = new HashMap<>();

    /// Struct instances indexed by variable name, populated during AST building
    private final Map<String, StructInstance> STRUCT_INSTANCES = new HashMap<>();

    /// Protocol definitions indexed by name, populated during AST building
    private final Map<String, ProtocolDefinition> PROTOCOL_DEFINITIONS = new HashMap<>();

    /// Macro definitions indexed by name, populated during AST building
    private final Map<String, MacroDefinition> MACRO_DEFINITIONS = new HashMap<>();

    /// Tracks libraries currently being resolved to detect circular dependencies
    private final Set<String> librariesBeingResolved = new HashSet<>();

    /// Library resolver for resolving "use library" statements
    private LibraryResolver libraryResolver = LibraryResolver.NONE;

    /**
     * Sets the library resolver used to resolve "use library" statements.
     */
    public void setLibraryResolver(LibraryResolver resolver) {
        this.libraryResolver = resolver != null ? resolver : LibraryResolver.NONE;
    }

    /**
     * Registers a definition in a map, throwing if a duplicate name is detected.
     *
     * @param definitions The map to register in
     * @param name The name to register
     * @param value The value to associate with the name
     * @param typeName The type name for error messages (e.g., "protocol", "struct", "macro")
     * @param <T> The type of definition
     */
    private <T> void registerDefinition(Map<String, T> definitions, String name, T value, String typeName) {
        if (definitions.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate " + typeName + " definition: " + name);
        }
        definitions.put(name, value);
    }

    /**
     * Builds a ResourceIdSet from a list of resource ID contexts.
     *
     * @param resourceIds The list of resource ID contexts to process
     * @param trackCtx The parser context to track (may be null)
     * @return A ResourceIdSet containing the parsed resource identifiers
     */
    private ResourceIdSet buildResourceIdSet(List<SFMLParser.ResourceIdContext> resourceIds, @Nullable ParserRuleContext trackCtx) {
        HashSet<ResourceIdentifier<?, ?, ?>> ids = resourceIds
                .stream()
                .map(this::visit)
                .map(ResourceIdentifier.class::cast)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        ResourceIdSet resourceIdSet = new ResourceIdSet(ids);
        if (trackCtx != null) {
            trackNode(resourceIdSet, trackCtx);
        }
        return resourceIdSet;
    }

    /// @return hierarchy of nodes; e.g., Program > Trigger > Block > IOStatement > LabelAccess > Label
    public List<Pair<ASTNode, ParserRuleContext>> getNodesUnderCursor(int cursorPos) {

        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getSecond() != null)
                .filter(pair -> pair.getSecond().start.getStartIndex() <= cursorPos
                                && pair.getSecond().stop.getStopIndex() >= cursorPos)
                .map(pair -> Pair.of(pair.getFirst().get(), pair.getSecond()))
                .filter(pair -> pair.getFirst() != null)
                .collect(Collectors.toList());
    }

    /// @return {@link #AST_NODE_CONTEXTS}.get({@code index})
    public Optional<ASTNode> getNodeAtIndex(int index) {

        if (index < 0 || index >= AST_NODE_CONTEXTS.size()) return Optional.empty();
        WeakReference<ASTNode> nodeRef = AST_NODE_CONTEXTS.get(index).getFirst();
        return Optional.ofNullable(nodeRef.get());
    }

    /// Used by {@link ForgetStatement} to track the provenance of dynamically generated {@link InputStatement} instances.
    /// We should use weak references for these dynamically generated nodes to let them get garbage collected; <a href="https://github.com/TeamDman/SuperFactoryManager/issues/405">#405</a>.
    public void setLocationFromOtherNode(
            ASTNode node,
            ASTNode otherNode
    ) {
        int index = getIndexForNode(otherNode);
        if (index < 0) {
            // Node not found in AST_NODE_CONTEXTS - this can happen for macro-expanded statements
            // In this case, we track the node without a context
            trackNode(node, null);
            return;
        }
        trackNode(node, AST_NODE_CONTEXTS.get(index).getSecond());
    }

    /// Used for client-server collaboration to make context menu actions work.
    /// The client calls {@link #getNodesUnderCursor(int)} and then {@link ca.teamdman.sfm.client.ProgramTokenContextActions#getContextAction(String, int)} to build the pick list.
    /// When a context action is invoked, a packet is sent to the server containing the index of the node so that the
    /// packet handler can do its work with the server's instance of the {@link ASTNode}.
    public int getIndexForNode(ASTNode node) {

        for (int i = 0; i < AST_NODE_CONTEXTS.size(); i++) {
            Pair<WeakReference<ASTNode>, ParserRuleContext> pair = AST_NODE_CONTEXTS.get(i);
            // Intentional reference equality check, don't forget the `.get()`!
            if (pair.getFirst().get() == node) {
                return i;
            }
        }
        return -1;
    }

    public Optional<ParserRuleContext> getContextForNode(ASTNode node) {

        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getFirst().get() == node)
                .map(Pair::getSecond)
                .findFirst();
    }

    public String getLineColumnForNode(ASTNode node) {
        // todo: return TranslatableContents
        return getContextForNode(node)
                .map(ctx -> "Line " + ctx.start.getLine() + ", Column " + ctx.start.getCharPositionInLine())
                .orElse("Unknown location");
    }

    @Override
    public StringHolder visitName(@Nullable SFMLParser.NameContext ctx) {

        if (ctx == null) return new StringHolder("");
        StringHolder name = visitString(ctx.string());
        trackNode(new ProgramName(name), ctx);
        return name;
    }

    @Override
    public ASTNode visitResource(SFMLParser.ResourceContext ctx) {

        var str = ctx
                .children
                .stream()
                .map(ParseTree::getText)
                .collect(Collectors.joining())
                .replaceAll("::", ":*:")
                .replaceAll(":$", ":*")
                .replaceAll("\\*", ".*")
                .toLowerCase(Locale.ROOT);

        var rtn = ResourceIdentifier.fromString(str);
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public ResourceIdentifier<?, ?, ?> visitStringResource(SFMLParser.StringResourceContext ctx) {

        var rtn = ResourceIdentifier.fromString(visitString(ctx.string()).value());
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public StringHolder visitString(SFMLParser.StringContext ctx) {

        var content = ctx.getText();
        String innerContent = content.substring(1, content.length() - 1).replaceAll("\\\\\"", "\"");
        StringHolder str = new StringHolder(innerContent);
        trackNode(str, ctx);
        return str;
    }

    @Override
    public Label visitRawLabel(SFMLParser.RawLabelContext ctx) {

        var label = new Label(ctx.getText());
        if (label.name().length() > Program.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label name cannot be longer than "
                    + Program.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        USED_LABELS.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public Label visitStringLabel(SFMLParser.StringLabelContext ctx) {

        var label = new Label(visitString(ctx.string()).value());
        if (label.name().length() > Program.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label name cannot be longer than "
                    + Program.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        USED_LABELS.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public Program visitProgram(SFMLParser.ProgramContext ctx) {

        if (SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disableProgramExecution)) {
            throw new AssertionError("Program execution is disabled via config");
        }
        var name = visitName(ctx.name());

        // Process library references
        var libraries = ctx
                .library()
                .stream()
                .map(this::visitLibrary)
                .collect(Collectors.toList());

        // Resolve library definitions and import them before processing local definitions
        for (LibraryStatement libraryStmt : libraries) {
            String libraryName = libraryStmt.blockLabel();

            // Check for circular dependency
            if (librariesBeingResolved.contains(libraryName)) {
                throw new IllegalArgumentException("Circular library dependency detected: " + libraryName);
            }

            librariesBeingResolved.add(libraryName);
            Optional<LibraryDefinitions> resolved;
            try {
                resolved = libraryResolver.resolve(libraryName);
            } finally {
                librariesBeingResolved.remove(libraryName);
            }

            if (resolved.isEmpty()) {
                throw new IllegalArgumentException("Library '" + libraryName + "' not found in cable network");
            }
            LibraryDefinitions libDefs = resolved.get();

            // Import protocols from library
            for (ProtocolDefinition proto : libDefs.protocols()) {
                if (PROTOCOL_DEFINITIONS.containsKey(proto.name())) {
                    throw new IllegalArgumentException("Duplicate protocol definition: " + proto.name() + " (imported from library '" + libraryStmt.blockLabel() + "')");
                }
                PROTOCOL_DEFINITIONS.put(proto.name(), proto);
            }

            // Import structs from library
            for (StructDefinition struct : libDefs.structs()) {
                if (STRUCT_DEFINITIONS.containsKey(struct.name())) {
                    throw new IllegalArgumentException("Duplicate struct definition: " + struct.name() + " (imported from library '" + libraryStmt.blockLabel() + "')");
                }
                STRUCT_DEFINITIONS.put(struct.name(), struct);
            }

            // Import macros from library
            for (MacroDefinition macro : libDefs.macros()) {
                if (MACRO_DEFINITIONS.containsKey(macro.name())) {
                    throw new IllegalArgumentException("Duplicate macro definition: " + macro.name() + " (imported from library '" + libraryStmt.blockLabel() + "')");
                }
                MACRO_DEFINITIONS.put(macro.name(), macro);
            }
        }

        // Process protocol definitions (local definitions can override or extend library ones)
        var protocolDefinitions = ctx
                .protocolDefinition()
                .stream()
                .map(this::visitProtocolDefinition)
                .collect(Collectors.toList());

        // Process struct definitions (they can implement protocols)
        var structDefinitions = ctx
                .structDefinition()
                .stream()
                .map(this::visitStructDefinition)
                .collect(Collectors.toList());

        // Process macro definitions (they can reference protocols)
        var macroDefinitions = ctx
                .macroDefinition()
                .stream()
                .map(this::visitMacroDefinition)
                .collect(Collectors.toList());

        // Process let statements next (they reference struct definitions)
        var letStatements = ctx
                .letStatement()
                .stream()
                .map(this::visitLetStatement)
                .collect(Collectors.toList());

        // Process triggers last (they can reference struct instances and macros)
        var triggers = ctx
                .trigger()
                .stream()
                .map(this::visit)
                .map(Trigger.class::cast)
                .collect(Collectors.toList());

        var labels = USED_LABELS
                .stream()
                .map(Label::name)
                .collect(Collectors.toSet());
        Program program = new Program(
                this,
                name.value(),
                libraries,
                protocolDefinitions,
                structDefinitions,
                macroDefinitions,
                letStatements,
                triggers,
                labels,
                USED_RESOURCES
        );
        trackNode(program, ctx);
        return program;
    }

    // ===== LIBRARIES =====

    public LibraryStatement visitLibrary(SFMLParser.LibraryContext ctx) {
        String blockLabel = visitString(ctx.string()).value();
        LibraryStatement libraryStmt = new LibraryStatement(blockLabel);
        trackNode(libraryStmt, ctx);
        return libraryStmt;
    }

    // ===== PROTOCOL DEFINITIONS =====

    public ProtocolDefinition visitProtocolDefinition(SFMLParser.ProtocolDefinitionContext ctx) {
        String name = ctx.identifier().getText();

        List<ProtocolField> fields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();

        for (SFMLParser.ProtocolFieldContext fieldCtx : ctx.protocolBody().protocolField()) {
            ProtocolField field = visitProtocolField(fieldCtx);

            // Check for duplicate field names
            if (!fieldNames.add(field.name())) {
                throw new IllegalArgumentException(
                        "Duplicate field name '" + field.name() + "' in protocol " + name
                );
            }

            fields.add(field);
        }

        ProtocolDefinition protocolDef = new ProtocolDefinition(name, fields);
        registerDefinition(PROTOCOL_DEFINITIONS, name, protocolDef, "protocol");
        trackNode(protocolDef, ctx);
        return protocolDef;
    }

    public ProtocolField visitProtocolField(SFMLParser.ProtocolFieldContext ctx) {
        String fieldName = ctx.identifier().getText();
        ProtocolFieldType type = visitProtocolFieldType(ctx.protocolFieldType());
        ProtocolField field = new ProtocolField(fieldName, type);
        trackNode(field, ctx);
        return field;
    }

    public ProtocolFieldType visitProtocolFieldType(SFMLParser.ProtocolFieldTypeContext ctx) {
        ProtocolFieldType type;
        if (ctx instanceof SFMLParser.SideAndSlotTypeContext) {
            type = ProtocolFieldType.SIDE_AND_SLOT;
        } else if (ctx instanceof SFMLParser.SideTypeContext) {
            type = ProtocolFieldType.SIDE_QUALIFIER;
        } else if (ctx instanceof SFMLParser.SlotTypeContext) {
            type = ProtocolFieldType.SLOT_QUALIFIER;
        } else if (ctx instanceof SFMLParser.LabelTypeContext) {
            type = ProtocolFieldType.LABEL;
        } else if (ctx instanceof SFMLParser.ResourceTypeContext) {
            type = ProtocolFieldType.RESOURCE;
        } else if (ctx instanceof SFMLParser.NumberTypeContext) {
            type = ProtocolFieldType.NUMBER;
        } else {
            throw new IllegalStateException("Unknown protocol field type");
        }
        trackNode(type, ctx);
        return type;
    }

    // ===== STRUCT DEFINITIONS =====

    public StructDefinition visitStructDefinition(SFMLParser.StructDefinitionContext ctx) {
        String name = ctx.identifier(0).getText();

        // Collect implemented protocols
        List<String> implementedProtocols = new ArrayList<>();
        for (int i = 1; i < ctx.identifier().size(); i++) {
            String protocolName = ctx.identifier(i).getText();
            if (!PROTOCOL_DEFINITIONS.containsKey(protocolName)) {
                throw new IllegalArgumentException("Unknown protocol: " + protocolName);
            }
            implementedProtocols.add(protocolName);
        }

        List<StructField> fields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();

        for (SFMLParser.StructFieldContext fieldCtx : ctx.structBody().structField()) {
            StructField field = visitStructField(fieldCtx);

            // Check for duplicate field names
            if (!fieldNames.add(field.name())) {
                throw new IllegalArgumentException(
                        "Duplicate field name '" + field.name() + "' in struct " + name
                );
            }

            fields.add(field);
        }

        StructDefinition structDef = new StructDefinition(name, implementedProtocols, fields);

        // Validate protocol conformance
        for (String protocolName : implementedProtocols) {
            validateProtocolConformance(structDef, PROTOCOL_DEFINITIONS.get(protocolName));
        }

        registerDefinition(STRUCT_DEFINITIONS, name, structDef, "struct");
        trackNode(structDef, ctx);
        return structDef;
    }

    /**
     * Validates that a struct properly implements all fields required by a protocol.
     */
    private void validateProtocolConformance(StructDefinition struct, ProtocolDefinition protocol) {
        for (ProtocolField protoField : protocol.fields()) {
            Optional<StructField> structField = struct.getField(protoField.name());

            if (structField.isEmpty()) {
                throw new IllegalArgumentException(
                        "Struct '" + struct.name() + "' is missing required field '"
                        + protoField.name() + "' from protocol '" + protocol.name() + "'"
                );
            }

            if (!protoField.type().matches(structField.get().value())) {
                throw new IllegalArgumentException(
                        "Struct '" + struct.name() + "' field '" + protoField.name()
                        + "' has wrong type. Expected " + protoField.type()
                        + " but got " + structField.get().value().getClass().getSimpleName()
                );
            }
        }
    }

    public StructField visitStructField(SFMLParser.StructFieldContext ctx) {
        String fieldName = ctx.identifier().getText();
        StructFieldValue value = visitStructFieldValue(ctx.structFieldValue());
        StructField field = new StructField(fieldName, value);
        trackNode(field, ctx);
        return field;
    }

    public StructFieldValue visitStructFieldValue(SFMLParser.StructFieldValueContext ctx) {
        // composite: sidequalifier slotqualifier?
        if (ctx.sidequalifier() != null) {
            SideQualifier sides = (SideQualifier) visit(ctx.sidequalifier());
            NumberRangeSet slots = visitSlotqualifier(ctx.slotqualifier());
            if (!slots.equals(NumberRangeSet.MAX_RANGE)) {
                // Has both sides and slots - composite value
                CompositeFieldValue composite = new CompositeFieldValue(sides, slots);
                trackNode(composite, ctx);
                return composite;
            }
            // Just sides
            return sides;
        }

        // slotqualifier only
        if (ctx.slotqualifier() != null) {
            return visitSlotqualifier(ctx.slotqualifier());
        }

        // resourceIdDisjunction
        if (ctx.resourceIdDisjunction() != null) {
            return visitResourceIdDisjunction(ctx.resourceIdDisjunction());
        }

        // label
        if (ctx.label() != null) {
            return (Label) visit(ctx.label());
        }

        // number
        if (ctx.number() != null) {
            return visitNumber(ctx.number());
        }

        throw new IllegalStateException("Unknown struct field value type");
    }

    public LetStatement visitLetStatement(SFMLParser.LetStatementContext ctx) {
        String variableName = ctx.identifier().getText();

        // Check for duplicate variable names
        if (STRUCT_INSTANCES.containsKey(variableName)) {
            throw new IllegalArgumentException("Duplicate variable name: " + variableName);
        }

        StructInstance instance = visitStructInstantiation(ctx.structInstantiation(), variableName);
        LetStatement letStatement = new LetStatement(variableName, instance);

        STRUCT_INSTANCES.put(variableName, instance);
        trackNode(letStatement, ctx);
        return letStatement;
    }

    public StructInstance visitStructInstantiation(SFMLParser.StructInstantiationContext ctx, String variableName) {
        String structName = ctx.identifier().getText();

        // Look up the struct definition
        StructDefinition definition = STRUCT_DEFINITIONS.get(structName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown struct: " + structName);
        }

        // Use the variable name as the label automatically
        Label label = new Label(variableName);
        USED_LABELS.add(label);

        // Create overrides map with the label
        Map<String, StructFieldValue> overrides = new LinkedHashMap<>();
        overrides.put("label", label);

        // Process optional WITH clause field overrides
        for (SFMLParser.StructFieldOverrideContext overrideCtx : ctx.structFieldOverride()) {
            String fieldName = overrideCtx.identifier().getText();

            // Validate that the field exists in the struct definition
            if (definition.getField(fieldName).isEmpty()) {
                throw new IllegalArgumentException(
                        "Unknown field '" + fieldName + "' in struct " + structName
                );
            }

            StructFieldValue value = visitStructFieldValue(overrideCtx.structFieldValue());
            overrides.put(fieldName, value);
        }

        StructInstance instance = new StructInstance(variableName, definition, overrides);
        trackNode(instance, ctx);
        return instance;
    }

    // ===== END STRUCT DEFINITIONS =====

    // ===== MACRO DEFINITIONS =====

    public MacroDefinition visitMacroDefinition(SFMLParser.MacroDefinitionContext ctx) {
        String name = ctx.identifier().getText();

        // Parse parameters
        List<MacroParameter> parameters = new ArrayList<>();
        if (ctx.macroParamList() != null) {
            for (SFMLParser.MacroParamContext paramCtx : ctx.macroParamList().macroParam()) {
                MacroParameter param = visitMacroParam(paramCtx);
                parameters.add(param);
            }
        }

        // Parse body
        List<MacroStatement> body = visitMacroBodyStatements(ctx.macroBody());

        MacroDefinition macroDef = new MacroDefinition(name, parameters, body);
        registerDefinition(MACRO_DEFINITIONS, name, macroDef, "macro");
        trackNode(macroDef, ctx);
        return macroDef;
    }

    public MacroParameter visitMacroParam(SFMLParser.MacroParamContext ctx) {
        String name = ctx.identifier(0).getText();
        String protocolConstraint = null;
        if (ctx.identifier().size() > 1) {
            protocolConstraint = ctx.identifier(1).getText();
            // Validate that the protocol exists
            if (!PROTOCOL_DEFINITIONS.containsKey(protocolConstraint)) {
                throw new IllegalArgumentException("Unknown protocol constraint: " + protocolConstraint);
            }
        }
        MacroParameter param = new MacroParameter(name, protocolConstraint);
        trackNode(param, ctx);
        return param;
    }

    private List<MacroStatement> visitMacroBodyStatements(SFMLParser.MacroBodyContext ctx) {
        List<MacroStatement> statements = new ArrayList<>();
        for (SFMLParser.MacroStatementContext stmtCtx : ctx.macroStatement()) {
            statements.add(visitMacroStatement(stmtCtx));
        }
        return statements;
    }

    public MacroStatement visitMacroStatement(SFMLParser.MacroStatementContext ctx) {
        if (ctx.macroInputStatement() != null) {
            return visitMacroInputStatement(ctx.macroInputStatement());
        } else if (ctx.macroOutputStatement() != null) {
            return visitMacroOutputStatement(ctx.macroOutputStatement());
        } else if (ctx.macroIfStatement() != null) {
            return visitMacroIfStatement(ctx.macroIfStatement());
        } else if (ctx.macroForgetStatement() != null) {
            return visitMacroForgetStatement(ctx.macroForgetStatement());
        }
        throw new IllegalStateException("Unknown macro statement type");
    }

    public MacroInputStatement visitMacroInputStatement(SFMLParser.MacroInputStatementContext ctx) {
        MacroLabelAccess labelAccess = visitMacroLabelAccess(ctx.macroLabelAccess());
        ResourceLimits resourceLimits = null;
        if (ctx.macroResourceLimits() != null) {
            resourceLimits = visitResourceLimitList(ctx.macroResourceLimits().resourceLimitList())
                    .withDefaultLimit(Limit.MAX_QUANTITY_NO_RETENTION);
        }
        boolean each = ctx.EACH() != null;
        MacroInputStatement stmt = new MacroInputStatement(labelAccess, resourceLimits, each);
        trackNode(stmt, ctx);
        return stmt;
    }

    public MacroOutputStatement visitMacroOutputStatement(SFMLParser.MacroOutputStatementContext ctx) {
        MacroLabelAccess labelAccess = visitMacroLabelAccess(ctx.macroLabelAccess());
        ResourceLimits resourceLimits = null;
        if (ctx.macroResourceLimits() != null) {
            resourceLimits = visitResourceLimitList(ctx.macroResourceLimits().resourceLimitList())
                    .withDefaultLimit(Limit.MAX_QUANTITY_MAX_RETENTION);
        }
        boolean each = ctx.EACH() != null;
        MacroOutputStatement stmt = new MacroOutputStatement(labelAccess, resourceLimits, each);
        trackNode(stmt, ctx);
        return stmt;
    }

    public MacroIfStatement visitMacroIfStatement(SFMLParser.MacroIfStatementContext ctx) {
        BoolExpr condition = (BoolExpr) visit(ctx.boolexpr());
        List<MacroStatement> thenBody = visitMacroBodyStatements(ctx.macroBody(0));
        List<MacroStatement> elseBody = ctx.macroBody().size() > 1
                ? visitMacroBodyStatements(ctx.macroBody(1))
                : List.of();
        MacroIfStatement stmt = new MacroIfStatement(condition, thenBody, elseBody);
        trackNode(stmt, ctx);
        return stmt;
    }

    public MacroForgetStatement visitMacroForgetStatement(SFMLParser.MacroForgetStatementContext ctx) {
        MacroForgetStatement stmt = new MacroForgetStatement();
        trackNode(stmt, ctx);
        return stmt;
    }

    public MacroLabelAccess visitMacroLabelAccess(SFMLParser.MacroLabelAccessContext ctx) {
        if (ctx instanceof SFMLParser.MacroParamLabelAccessContext paramCtx) {
            String paramName = paramCtx.identifier().getText();
            MacroLabelAccess access = MacroLabelAccess.parameter(paramName);
            trackNode(access, ctx);
            return access;
        } else if (ctx instanceof SFMLParser.MacroStructLabelAccessContext structCtx) {
            String paramName = structCtx.identifier(0).getText();
            String fieldName = structCtx.identifier(1).getText();
            SideQualifier sideOverride = structCtx.sidequalifier() != null
                    ? (SideQualifier) visit(structCtx.sidequalifier())
                    : null;
            NumberRangeSet slotOverride = structCtx.slotqualifier() != null
                    ? visitSlotqualifier(structCtx.slotqualifier())
                    : null;
            MacroLabelAccess access = MacroLabelAccess.structAccess(paramName, fieldName, sideOverride, slotOverride);
            trackNode(access, ctx);
            return access;
        }
        throw new IllegalStateException("Unknown macro label access type");
    }

    // ===== EXPAND STATEMENTS =====

    @Override
    public ExpandStatement visitExpandStatement(SFMLParser.ExpandStatementContext ctx) {
        String macroName = ctx.identifier().getText();

        // Look up the macro
        MacroDefinition macro = MACRO_DEFINITIONS.get(macroName);
        if (macro == null) {
            throw new IllegalArgumentException("Unknown macro: " + macroName);
        }

        // Parse arguments
        List<ExpandArgument> arguments = new ArrayList<>();
        if (ctx.expandArgList() != null) {
            for (SFMLParser.ExpandArgContext argCtx : ctx.expandArgList().expandArg()) {
                ExpandArgument arg = visitExpandArg(argCtx);
                arguments.add(arg);
            }
        }

        // Validate argument count
        if (arguments.size() != macro.parameters().size()) {
            throw new IllegalArgumentException(
                    "Macro '" + macroName + "' expects " + macro.parameters().size()
                    + " arguments but got " + arguments.size()
            );
        }

        // Validate protocol constraints
        for (int i = 0; i < arguments.size(); i++) {
            MacroParameter param = macro.parameters().get(i);
            ExpandArgument arg = arguments.get(i);

            if (param.protocolConstraint() != null) {
                // Argument must be a struct variable that implements the protocol
                if (arg.isStringLiteral()) {
                    throw new IllegalArgumentException(
                            "Macro parameter '" + param.name() + "' requires a struct implementing protocol '"
                            + param.protocolConstraint() + "', but got a string literal"
                    );
                }

                StructInstance instance = STRUCT_INSTANCES.get(arg.value());
                if (instance == null) {
                    throw new IllegalArgumentException(
                            "Macro parameter '" + param.name() + "' requires a struct implementing protocol '"
                            + param.protocolConstraint() + "', but '" + arg.value() + "' is not a struct variable"
                    );
                }

                if (!instance.definition().implementsProtocol(param.protocolConstraint())) {
                    throw new IllegalArgumentException(
                            "Struct '" + instance.definition().name() + "' does not implement protocol '"
                            + param.protocolConstraint() + "' required by macro parameter '" + param.name() + "'"
                    );
                }
            }
        }

        // Expand the macro
        List<Statement> expandedStatements = expandMacro(macro, arguments);

        ExpandStatement expandStmt = new ExpandStatement(macroName, arguments, expandedStatements);
        trackNode(expandStmt, ctx);
        return expandStmt;
    }

    public ExpandArgument visitExpandArg(SFMLParser.ExpandArgContext ctx) {
        if (ctx.identifier() != null) {
            return ExpandArgument.identifier(ctx.identifier().getText());
        } else if (ctx.string() != null) {
            return ExpandArgument.stringLiteral(visitString(ctx.string()).value());
        }
        throw new IllegalStateException("Unknown expand argument type");
    }

    /**
     * Expands a macro with the given arguments, producing a list of statements.
     */
    private List<Statement> expandMacro(MacroDefinition macro, List<ExpandArgument> arguments) {
        List<Statement> result = new ArrayList<>();

        // Build argument map
        Map<String, ExpandArgument> argMap = new HashMap<>();
        for (int i = 0; i < macro.parameters().size(); i++) {
            argMap.put(macro.parameters().get(i).name(), arguments.get(i));
        }

        // Expand each macro statement
        for (MacroStatement macroStmt : macro.body()) {
            result.addAll(expandMacroStatement(macroStmt, argMap));
        }

        return result;
    }

    /**
     * Expands a single macro statement into regular statements.
     */
    private List<Statement> expandMacroStatement(MacroStatement macroStmt, Map<String, ExpandArgument> argMap) {
        if (macroStmt instanceof MacroInputStatement input) {
            return List.of(expandMacroInput(input, argMap));
        } else if (macroStmt instanceof MacroOutputStatement output) {
            return List.of(expandMacroOutput(output, argMap));
        } else if (macroStmt instanceof MacroForgetStatement) {
            return List.of(new ForgetStatement(USED_LABELS));
        } else if (macroStmt instanceof MacroIfStatement macroIf) {
            List<Statement> thenStatements = new ArrayList<>();
            for (MacroStatement s : macroIf.thenBody()) {
                thenStatements.addAll(expandMacroStatement(s, argMap));
            }
            List<Statement> elseStatements = new ArrayList<>();
            for (MacroStatement s : macroIf.elseBody()) {
                elseStatements.addAll(expandMacroStatement(s, argMap));
            }
            return List.of(new IfStatement(
                    macroIf.condition(),
                    new Block(thenStatements),
                    new Block(elseStatements)
            ));
        }
        throw new IllegalStateException("Unknown macro statement type: " + macroStmt.getClass());
    }

    /**
     * Expands a macro input statement.
     */
    private InputStatement expandMacroInput(MacroInputStatement macroInput, Map<String, ExpandArgument> argMap) {
        LabelAccess labelAccess = resolveMacroLabelAccess(macroInput.labelAccess(), argMap);
        ResourceLimits limits = macroInput.resourceLimits() != null
                ? macroInput.resourceLimits()
                : new ResourceLimits(List.of(ResourceLimit.TAKE_ALL_LEAVE_NONE), ResourceIdSet.EMPTY);
        return new InputStatement(labelAccess, limits, macroInput.each());
    }

    /**
     * Expands a macro output statement.
     */
    private OutputStatement expandMacroOutput(MacroOutputStatement macroOutput, Map<String, ExpandArgument> argMap) {
        LabelAccess labelAccess = resolveMacroLabelAccess(macroOutput.labelAccess(), argMap);
        ResourceLimits limits = macroOutput.resourceLimits() != null
                ? macroOutput.resourceLimits()
                : new ResourceLimits(List.of(ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT), ResourceIdSet.EMPTY);
        return new OutputStatement(labelAccess, limits, macroOutput.each(), false);
    }

    /**
     * Resolves a macro label access to a concrete LabelAccess.
     */
    private LabelAccess resolveMacroLabelAccess(MacroLabelAccess macroAccess, Map<String, ExpandArgument> argMap) {
        ExpandArgument arg = argMap.get(macroAccess.parameterOrVariable());

        // Early null check with clear error message
        if (arg == null) {
            throw new IllegalStateException("Unknown macro parameter: " + macroAccess.parameterOrVariable());
        }

        if (macroAccess.isStructAccess()) {
            // This is a struct field access: param using field
            if (arg.isStringLiteral()) {
                throw new IllegalStateException(
                        "Macro struct access requires a struct variable, got: " + macroAccess.parameterOrVariable()
                );
            }

            StructInstance instance = STRUCT_INSTANCES.get(arg.value());
            if (instance == null) {
                throw new IllegalStateException("Unknown struct variable in macro expansion: " + arg.value());
            }

            Label label = instance.getLabel().orElseThrow(() ->
                    new IllegalStateException("Struct instance " + arg.value() + " has no label")
            );

            // Resolve sides and slots from the field
            String fieldName = macroAccess.fieldName();
            Optional<StructFieldValue> fieldValue = instance.resolveField(fieldName);
            if (fieldValue.isEmpty()) {
                throw new IllegalStateException(
                        "Unknown field '" + fieldName + "' in struct " + instance.definition().name()
                );
            }

            SideQualifier sides = SideQualifier.NULL;
            NumberRangeSet slots = NumberRangeSet.MAX_RANGE;

            StructFieldValue resolved = fieldValue.get();
            if (resolved instanceof CompositeFieldValue composite) {
                sides = composite.sides();
                slots = composite.slots();
            } else if (resolved instanceof SideQualifier sq) {
                sides = sq;
            } else if (resolved instanceof NumberRangeSet nrs) {
                slots = nrs;
            }

            // Apply overrides
            if (macroAccess.sideOverride() != null) {
                sides = macroAccess.sideOverride();
            }
            if (macroAccess.slotOverride() != null) {
                slots = macroAccess.slotOverride();
            }

            return new LabelAccess(
                    List.of(label),
                    sides,
                    slots,
                    RoundRobin.disabled(),
                    new StructAccess(arg.value(), fieldName)
            );
        } else {
            // This is a simple parameter reference
            Label label;
            if (arg.isStringLiteral()) {
                // String literal becomes a label directly
                label = new Label(arg.value());
                USED_LABELS.add(label);
            } else {
                // Check if it's a struct variable or a plain label
                StructInstance instance = STRUCT_INSTANCES.get(arg.value());
                if (instance != null) {
                    label = instance.getLabel().orElseThrow(() ->
                            new IllegalStateException("Struct instance " + arg.value() + " has no label")
                    );
                } else {
                    // Treat as a label name
                    label = new Label(arg.value());
                    USED_LABELS.add(label);
                }
            }

            return new LabelAccess(
                    List.of(label),
                    SideQualifier.NULL,
                    NumberRangeSet.MAX_RANGE,
                    RoundRobin.disabled(),
                    null
            );
        }
    }

    // ===== END MACRO DEFINITIONS =====

    @Override
    public ASTNode visitTimerTrigger(SFMLParser.TimerTriggerContext ctx) {
        // create timer trigger
        var time = (Interval) visit(ctx.interval());
        var block = visitBlock(ctx.block());
        TimerTrigger timerTrigger = new TimerTrigger(time, block);

        // get default min interval
        int minInterval = timerTrigger.usesOnlyForgeEnergyResourceIO()
                          ? SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO)
                          : SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicks);

        // validate interval
        if (time.ticks() < minInterval) {
            throw new IllegalArgumentException("Minimum trigger interval is " + minInterval + " ticks.");
        }

        trackNode(timerTrigger, ctx);
        return timerTrigger;
    }

    @Override
    public ASTNode visitBooleanRedstone(SFMLParser.BooleanRedstoneContext ctx) {

        ComparisonOperator comp = ComparisonOperator.GREATER_OR_EQUAL;
        Number num = new Number(0);
        if (ctx.comparisonOp() != null && ctx.number() != null) {
            comp = visitComparisonOp(ctx.comparisonOp());
            num = visitNumber(ctx.number());
        }
        if (num.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Redstone signal strength cannot be greater than " + Integer.MAX_VALUE);
        }
        BoolExpr boolExpr = new BoolRedstone(comp, (int) num.value());
        trackNode(boolExpr, ctx);
        return boolExpr;
    }

    @Override
    public ASTNode visitPulseTrigger(SFMLParser.PulseTriggerContext ctx) {

        var block = visitBlock(ctx.block());
        RedstoneTrigger redstoneTrigger = new RedstoneTrigger(block);
        trackNode(redstoneTrigger, ctx);
        return redstoneTrigger;
    }

    @Override
    public Number visitNumber(SFMLParser.NumberContext ctx) {

        Number number = new Number(Long.parseLong(ctx.getText()));
        trackNode(number, ctx);
        return number;
    }

    @Override
    public ASTNode visitIntervalSpace(SFMLParser.IntervalSpaceContext ctx) {

        TerminalNode firstNumber = ctx.NUMBER(0);
        int ticks;
        if (firstNumber == null) {
            ticks = 1;
        } else {
            ticks = Integer.parseInt(firstNumber.getText());
        }
        if (ctx.SECONDS() != null || ctx.SECOND() != null) {
            ticks *= 20;
        }

        Interval.IntervalAlignment alignment = Interval.IntervalAlignment.LOCAL;
        if (ctx.GLOBAL() != null) {
            alignment = Interval.IntervalAlignment.GLOBAL;
        }

        int offset = 0;
        TerminalNode secondNumber = ctx.NUMBER(1);
        if (secondNumber != null) {
            offset = Integer.parseInt(secondNumber.getText());
            if (ctx.SECONDS() != null || ctx.SECOND() != null) {
                offset *= 20;
            }
        }

        Interval interval = new Interval(ticks, alignment, offset);
        trackNode(interval, ctx);
        return interval;
    }

    @Override
    public ASTNode visitIntervalNoSpace(SFMLParser.IntervalNoSpaceContext ctx) {

        String firstNumber = ctx.NUMBER_WITH_G_SUFFIX().getText();
        String front = firstNumber.substring(0, firstNumber.length() - 1);
        int ticks = Integer.parseInt(front);
        if (ctx.SECONDS() != null || ctx.SECOND() != null) {
            ticks *= 20;
        }

        Interval.IntervalAlignment alignment = Interval.IntervalAlignment.GLOBAL;

        int offset = 0;
        TerminalNode secondNumber = ctx.NUMBER();
        if (secondNumber != null) {
            offset = Integer.parseInt(secondNumber.getText());
            if (ctx.SECONDS() != null || ctx.SECOND() != null) {
                offset *= 20;
            }
        }

        Interval interval = new Interval(ticks, alignment, offset);
        trackNode(interval, ctx);
        return interval;
    }

    @Override
    public InputStatement visitInputStatement(SFMLParser.InputStatementContext ctx) {

        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitInputResourceLimits(ctx.inputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        InputStatement inputStatement = new InputStatement(labelAccess, matchers.withExclusions(exclusions), each);
        trackNode(inputStatement, ctx);
        return inputStatement;
    }

    @Override
    public OutputStatement visitOutputStatement(SFMLParser.OutputStatementContext ctx) {

        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitOutputResourceLimits(ctx.outputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        boolean emptySlotsOnly = ctx.emptyslots() != null;
        OutputStatement outputStatement = new OutputStatement(
                labelAccess,
                matchers.withExclusions(exclusions),
                each,
                emptySlotsOnly
        );
        trackNode(outputStatement, ctx);
        return outputStatement;
    }

    public LabelAccess visitLabelAccess(SFMLParser.LabelAccessContext ctx) {
        // Delegate to the appropriate alternative visitor
        return (LabelAccess) visit(ctx);
    }

    @Override
    public LabelAccess visitDirectLabelAccess(SFMLParser.DirectLabelAccessContext ctx) {
        var directionQualifierCtx = ctx.sidequalifier();
        SideQualifier sideQualifier;
        if (directionQualifierCtx == null) {
            sideQualifier = SideQualifier.NULL;
        } else {
            sideQualifier = (SideQualifier) visit(directionQualifierCtx);
        }
        LabelAccess labelAccess = new LabelAccess(
                ctx.label().stream().map(this::visit).map(Label.class::cast).collect(Collectors.toList()),
                sideQualifier,
                visitSlotqualifier(ctx.slotqualifier()),
                visitRoundrobin(ctx.roundrobin()),
                null // No struct access for direct label access
        );
        trackNode(labelAccess, ctx);
        return labelAccess;
    }

    @Override
    public LabelAccess visitStructLabelAccess(SFMLParser.StructLabelAccessContext ctx) {
        String variableName = ctx.identifier(0).getText();
        String fieldName = ctx.identifier(1).getText();

        // Validate that the variable exists
        StructInstance instance = STRUCT_INSTANCES.get(variableName);
        if (instance == null) {
            throw new IllegalArgumentException("Unknown struct variable: " + variableName);
        }

        // Validate that the field exists
        Optional<StructFieldValue> fieldValue = instance.resolveField(fieldName);
        if (fieldValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown field '" + fieldName + "' in struct variable " + variableName
            );
        }

        // Get the label from the struct instance
        Label label = instance.getLabel().orElseThrow(() ->
                new IllegalStateException("Struct instance " + variableName + " has no label")
        );

        // Resolve sides and slots from the field value
        SideQualifier sides = SideQualifier.NULL;
        NumberRangeSet slots = NumberRangeSet.MAX_RANGE;

        StructFieldValue resolvedField = fieldValue.get();
        if (resolvedField instanceof CompositeFieldValue composite) {
            sides = composite.sides();
            slots = composite.slots();
        } else if (resolvedField instanceof SideQualifier sq) {
            sides = sq;
        } else if (resolvedField instanceof NumberRangeSet nrs) {
            slots = nrs;
        }

        // Allow explicit side/slot overrides in the USING clause
        if (ctx.sidequalifier() != null) {
            sides = (SideQualifier) visit(ctx.sidequalifier());
        }
        if (ctx.slotqualifier() != null) {
            slots = visitSlotqualifier(ctx.slotqualifier());
        }

        StructAccess structAccess = new StructAccess(variableName, fieldName);
        trackNode(structAccess, ctx);

        LabelAccess labelAccess = new LabelAccess(
                List.of(label),
                sides,
                slots,
                RoundRobin.disabled(),
                structAccess
        );
        trackNode(labelAccess, ctx);
        return labelAccess;
    }

    @Override
    public RoundRobin visitRoundrobin(@Nullable SFMLParser.RoundrobinContext ctx) {

        if (ctx == null) return RoundRobin.disabled();
        RoundRobin rtn = ctx.BLOCK() != null
                         ? new RoundRobin(RoundRobin.Behaviour.BY_BLOCK)
                         : new RoundRobin(RoundRobin.Behaviour.BY_LABEL);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public IfStatement visitIfStatement(SFMLParser.IfStatementContext ctx) {

        var conditions = ctx
                .boolexpr()
                .stream()
                .map(this::visit)
                .map(BoolExpr.class::cast)
                .collect(Collectors.toCollection(ArrayDeque::new));
        var blocks = ctx.block().stream()
                .map(this::visitBlock)
                .collect(Collectors.toCollection(ArrayDeque::new));

        IfStatement nestedStatement;
        if (conditions.size() < blocks.size()) {
            Block elseBlock = blocks.removeLast();
            Block ifBlock = blocks.removeLast();
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    ifBlock,
                    elseBlock
            );
        } else {
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    blocks.removeLast(),
                    new Block(List.of())
            );
        }
        while (!blocks.isEmpty()) {
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    blocks.removeLast(),
                    new Block(List.of(nestedStatement))
            );
        }
        if (!conditions.isEmpty()) {
            throw new IllegalStateException("If statement construction failed to consume all conditions");
        }

        trackNode(nestedStatement, ctx);
        return nestedStatement;
    }

    @Override
    public BoolExpr visitBooleanHas(SFMLParser.BooleanHasContext ctx) {

        var setOperator = visitSetOp(ctx.setOp());
        var labelAccess = visitLabelAccess(ctx.labelAccess());
        ComparisonOperator comparisonOperator = visitComparisonOp(ctx.comparisonOp());
        Number num = visitNumber(ctx.number());
        ResourceIdSet resourceIdSet;
        if (ctx.resourceIdDisjunction() == null) {
            resourceIdSet = ResourceIdSet.MATCH_ALL;
        } else {
            resourceIdSet = visitResourceIdDisjunction(ctx.resourceIdDisjunction());
        }
        With with;
        if (ctx.with() == null) {
            with = With.ALWAYS_TRUE;
        } else {
            with = (With) visit(ctx.with());
        }
        ResourceIdSet except;
        if (ctx.resourceIdList() == null) {
            except = ResourceIdSet.EMPTY;
        } else {
            except = visitResourceIdList(ctx.resourceIdList());
        }
        BoolHas rtn = new BoolHas(
                setOperator,
                labelAccess,
                comparisonOperator,
                num.value(),
                resourceIdSet,
                with,
                except
        );
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public SetOperator visitSetOp(@Nullable SFMLParser.SetOpContext ctx) {

        if (ctx == null) return SetOperator.OVERALL;
        SetOperator from = SetOperator.from(ctx.getText());
        trackNode(from, ctx);
        return from;
    }

    @Override
    public ComparisonOperator visitComparisonOp(SFMLParser.ComparisonOpContext ctx) {

        ComparisonOperator from = ComparisonOperator.from(ctx.getText());
        trackNode(from, ctx);
        return from;
    }

    @Override
    public BoolExpr visitBooleanTrue(SFMLParser.BooleanTrueContext ctx) {

        BoolExpr rtn = new BoolTrue();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanFalse(SFMLParser.BooleanFalseContext ctx) {

        BoolExpr rtn = new BoolFalse();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanParen(SFMLParser.BooleanParenContext ctx) {

        BoolExpr rtn = new BoolParen((BoolExpr) visit(ctx.boolexpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanNegation(SFMLParser.BooleanNegationContext ctx) {

        BoolExpr rtn = new BoolNegation((BoolExpr) visit(ctx.boolexpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr rtn = new BoolConjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr rtn = new BoolDisjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public Limit visitQuantityRetentionLimit(SFMLParser.QuantityRetentionLimitContext ctx) {

        var quantity = visitQuantity(ctx.quantity());
        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(quantity, retain);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public ResourceIdSet visitResourceExclusion(@Nullable SFMLParser.ResourceExclusionContext ctx) {

        if (ctx == null) return ResourceIdSet.EMPTY;
        var resourceIdSet = visitResourceIdList(ctx.resourceIdList());
        trackNode(resourceIdSet, ctx);
        return resourceIdSet;
    }

    /// This one uses COMMA instead of OR to separate items
    @Override
    public ResourceIdSet visitResourceIdList(@Nullable SFMLParser.ResourceIdListContext ctx) {
        if (ctx == null) return ResourceIdSet.EMPTY;
        return buildResourceIdSet(ctx.resourceId(), ctx);
    }

    /// This one uses OR instead of COMMA to separate items
    @Override
    public ResourceIdSet visitResourceIdDisjunction(@Nullable SFMLParser.ResourceIdDisjunctionContext ctx) {
        if (ctx == null) return ResourceIdSet.EMPTY;
        return buildResourceIdSet(ctx.resourceId(), ctx);
    }

    @Override
    public ResourceLimits visitInputResourceLimits(@Nullable SFMLParser.InputResourceLimitsContext ctx) {

        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.TAKE_ALL_LEAVE_NONE), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_NO_RETENTION);
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimits visitOutputResourceLimits(@Nullable SFMLParser.OutputResourceLimitsContext ctx) {

        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_MAX_RETENTION);
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimits visitResourceLimitList(SFMLParser.ResourceLimitListContext ctx) {

        ResourceLimits resourceLimits = new ResourceLimits(
                ctx.resourceLimit().stream()
                        .map(this::visitResourceLimit)
                        .collect(Collectors.toList()),
                ResourceIdSet.EMPTY
        );
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimit visitResourceLimit(SFMLParser.ResourceLimitContext ctx) {

        ResourceIdSet resourceIds;
        if (ctx.resourceIdDisjunction() == null) {
            resourceIds = ResourceIdSet.MATCH_ALL;
        } else {
            resourceIds = visitResourceIdDisjunction(ctx.resourceIdDisjunction());
        }

        Limit limit;
        if (ctx.limit() == null) {
            limit = Limit.UNSET;
        } else {
            limit = (Limit) visit(ctx.limit());
        }

        With with;
        if (ctx.with() == null) {
            with = With.ALWAYS_TRUE;
        } else {
            with = (With) visit(ctx.with());
        }

        ResourceLimit resourceLimit = new ResourceLimit(resourceIds, limit, with);

        trackNode(resourceLimit, ctx);
        return resourceLimit;
    }

    @Override
    public ASTNode visitWith(SFMLParser.WithContext ctx) {

        WithClause clause = (WithClause) visit(ctx.withClause());
        With.WithMode mode = ctx.WITHOUT() != null ? With.WithMode.WITHOUT : With.WithMode.WITH;
        With rtn = new With(clause, mode);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithTag visitWithTag(SFMLParser.WithTagContext ctx) {

        WithTag rtn = new WithTag((TagMatcher) visit(ctx.tagMatcher()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithConjunction visitWithConjunction(SFMLParser.WithConjunctionContext ctx) {

        var left = (WithClause) visit(ctx.withClause(0));
        var right = (WithClause) visit(ctx.withClause(1));
        WithConjunction rtn = new WithConjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithParen visitWithParen(SFMLParser.WithParenContext ctx) {

        var inner = (WithClause) visit(ctx.withClause());
        WithParen rtn = new WithParen(inner);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithNegation visitWithNegation(SFMLParser.WithNegationContext ctx) {

        var inner = (WithClause) visit(ctx.withClause());
        WithNegation rtn = new WithNegation(inner);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithDisjunction visitWithDisjunction(SFMLParser.WithDisjunctionContext ctx) {

        var left = (WithClause) visit(ctx.withClause(0));
        var right = (WithClause) visit(ctx.withClause(1));
        WithDisjunction rtn = new WithDisjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public TagMatcher visitTagMatcher(SFMLParser.TagMatcherContext ctx) {

        ArrayDeque<String> identifiers = ctx
                .identifier()
                .stream()
                .map(ParseTree::getText)
                .map(s -> s.replaceAll("\\*", ".*")) // convert * to .*
                .collect(Collectors.toCollection(ArrayDeque::new));
        TagMatcher rtn;
        if (ctx.COLON() == null) {
            // wildcard namespace
            rtn = TagMatcher.fromPath(identifiers);
        } else {
            // namespace specified
            rtn = TagMatcher.fromNamespaceAndPath(identifiers.pop(), identifiers);
        }
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberRangeSet visitSlotqualifier(@Nullable SFMLParser.SlotqualifierContext ctx) {

        NumberRangeSet numberRangeSet = visitRangeset(ctx == null ? null : ctx.rangeset());
        if (ctx != null) {
            trackNode(numberRangeSet, ctx);
        }
        return numberRangeSet;
    }

    @Override
    public ForgetStatement visitForgetStatement(SFMLParser.ForgetStatementContext ctx) {

        Set<Label> labels = ctx
                .label()
                .stream()
                .map(this::visit)
                .map(Label.class::cast)
                .collect(Collectors.toSet());
        if (labels.isEmpty()) {
            labels = USED_LABELS;
        }
        ForgetStatement rtn = new ForgetStatement(labels);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberRangeSet visitRangeset(@Nullable SFMLParser.RangesetContext ctx) {

        if (ctx == null) return NumberRangeSet.MAX_RANGE;
        NumberRangeSet numberRangeSet = new NumberRangeSet(
                ctx
                        .range()
                        .stream()
                        .map(this::visitRange)
                        .toArray(NumberRange[]::new)
        );
        trackNode(numberRangeSet, ctx);
        return numberRangeSet;
    }

    @Override
    public NumberRange visitRange(SFMLParser.RangeContext ctx) {

        var iter = ctx.number().stream().map(this::visitNumber).mapToLong(Number::value).iterator();
        var start = iter.next();
        if (iter.hasNext()) {
            var end = iter.next();
            NumberRange numberRange = new NumberRange(start, end);
            trackNode(numberRange, ctx);
            return numberRange;
        } else {
            NumberRange numberRange = new NumberRange(start, start);
            trackNode(numberRange, ctx);
            return numberRange;
        }
    }

    @Override
    public Limit visitRetentionLimit(SFMLParser.RetentionLimitContext ctx) {

        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(ResourceQuantity.UNSET, retain);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public Limit visitQuantityLimit(SFMLParser.QuantityLimitContext ctx) {

        var quantity = visitQuantity(ctx.quantity());
        Limit limit = new Limit(quantity, ResourceQuantity.UNSET);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public ResourceQuantity visitRetention(@Nullable SFMLParser.RetentionContext ctx) {

        if (ctx == null)
            return ResourceQuantity.UNSET;
        ResourceQuantity quantity = new ResourceQuantity(
                visitNumber(ctx.number()),
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public ResourceQuantity visitQuantity(@Nullable SFMLParser.QuantityContext ctx) {

        if (ctx == null) return ResourceQuantity.MAX_QUANTITY;
        ResourceQuantity quantity = new ResourceQuantity(
                visitNumber(ctx.number()),
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public SideQualifier visitEachSide(SFMLParser.EachSideContext ctx) {

        var rtn = SideQualifier.ALL;
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public SideQualifier visitListedSides(SFMLParser.ListedSidesContext ctx) {

        SideQualifier sideQualifier = new SideQualifier(
                ctx.side().stream()
                        .map(this::visitSide)
                        .toList()
        );
        trackNode(sideQualifier, ctx);
        return sideQualifier;
    }

    @Override
    public Side visitSide(SFMLParser.SideContext ctx) {

        Side side = Side.valueOf(ctx.getText().toUpperCase(Locale.ROOT));
        trackNode(side, ctx);
        return side;
    }

    @Override
    public Block visitBlock(@Nullable SFMLParser.BlockContext ctx) {

        if (ctx == null) return new Block(Collections.emptyList());
        var statements = ctx
                .statement()
                .stream()
                .map(this::visit)
                .map(Statement.class::cast)
                .collect(Collectors.toList());
        Block block = new Block(statements);
        trackNode(block, ctx);
        return block;
    }

    /// Tracks an {@link ASTNode} and its {@link ParserRuleContext} for later retrieval for editor context actions.
    private void trackNode(
            ASTNode node,
            ParserRuleContext ctx
    ) {

        WeakReference<ASTNode> nodeRef = new WeakReference<>(node);

        AST_NODE_CONTEXTS.add(new Pair<>(nodeRef, ctx));
    }

}
