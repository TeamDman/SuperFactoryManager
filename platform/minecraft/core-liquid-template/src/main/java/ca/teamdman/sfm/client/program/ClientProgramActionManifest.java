package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.action.SFMClientActionDescriptor;
import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfml.ast.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Client-only linking/type pass over the common AST, with literal action IDs and exact data subjects. */
public record ClientProgramActionManifest(Set<ResourceLocation> capabilities,
                                          Map<ResourceLocation, Set<SFMClientActionDescriptor.DataScope>> scopes) {
    public static final int MAX_VARIABLES = 64;
    public static final int MAX_ACTIONS = 32;
    public static final int MAX_SCOPES = 64;

    public ClientProgramActionManifest {
        capabilities = Set.copyOf(capabilities);
        if (capabilities.size() > ClientProgramConsentStore.MAX_CAPABILITIES) {
            throw new IllegalArgumentException("Client action manifest exceeds the consent capability budget");
        }
        Map<ResourceLocation, Set<SFMClientActionDescriptor.DataScope>> copied = new HashMap<>();
        scopes.forEach((id, values) -> copied.put(id, Set.copyOf(values)));
        scopes = Map.copyOf(copied);
    }

    public boolean permits(ResourceLocation action, SFMClientActionDescriptor.DataScope scope) {
        return scopes.getOrDefault(action, Set.of()).contains(scope);
    }

    public static ClientProgramActionManifest compile(Program program, SFMClientProgramActionDispatcher.Lookup actions) {
{% if features.client_frame_language %}
        Set<ResourceLocation> permissions = new HashSet<>(Set.of(ClientProgramConsentGate.EXECUTE));
        Map<ResourceLocation, Set<SFMClientActionDescriptor.DataScope>> scopes = new HashMap<>();
        for (Trigger trigger : program.triggers()) {
            if (!(trigger instanceof FrameTrigger frame)) throw new IllegalArgumentException("Client program requires frame triggers");
            link(frame.block(), new HashMap<>(), actions, permissions, scopes);
        }
        return new ClientProgramActionManifest(permissions, scopes);
{% else %}
        throw new IllegalArgumentException("Client program requires frame triggers");
{% endif %}
    }

    private record ValueType(SFMValueSchema schema, Optional<SFMValue> constant) {}

    private static void link(Block block, Map<String, ValueType> variables,
                             SFMClientProgramActionDispatcher.Lookup actions, Set<ResourceLocation> permissions,
                             Map<ResourceLocation, Set<SFMClientActionDescriptor.DataScope>> scopes) {
        for (Statement statement : block.statements()) {
{% if features.client_frame_render %}
            if (statement instanceof RenderImageStatement) {
                permissions.add(ClientProgramConsentGate.RENDER);
{% endif %}
{% if features.client_frame_render %}
            } else if (statement instanceof LetStatement let) {
{% else %}
            if (statement instanceof LetStatement let) {
{% endif %}
                ValueType type;
                if (let.expression() instanceof ClientValueExpression.JsonLiteral literal) {
                    type = new ValueType(SFMValueSchema.literal(literal.value()), Optional.of(literal.value()));
                } else if (let.expression() instanceof ClientValueExpression.Field field) {
                    ValueType source = require(variables, field.variable());
                    Optional<SFMValue> constant = source.constant().map(value -> field(value, field.field()));
                    type = new ValueType(fieldSchema(source.schema(), field.field()), constant);
                } else if (let.expression() instanceof ClientValueExpression.Invoke invoke) {
                    var binding = actions.find(invoke.action()).orElseThrow(() -> new IllegalArgumentException(
                            "Action has no typed program handler: " + invoke.action()));
                    var descriptor = binding.descriptor();
                    if (!descriptor.actionId().equals(invoke.action())
                        || descriptor.executionSide() != SFMClientActionDescriptor.ExecutionSide.CLIENT) {
                        throw new IllegalArgumentException("Action is not available on the client: " + invoke.action());
                    }
                    ValueType argument = require(variables, invoke.argument());
                    SFMValue constant = argument.constant().orElseThrow(() -> new IllegalArgumentException(
                            "Action arguments must currently have a statically resolvable value and target: " + invoke.argument()));
                    var checked = descriptor.checkInput(constant);
                    if (checked instanceof SFMClientActionDescriptor.InputCheck.Rejected rejected) {
                        throw new IllegalArgumentException("Invalid input for " + invoke.action() + ": "
                                + rejected.failure().code() + " at " + rejected.failure().path());
                    }
                    var subjects = ((SFMClientActionDescriptor.InputCheck.Accepted) checked).dataScopes();
                    permissions.add(descriptor.controlPermission());
                    subjects.forEach(scope -> permissions.add(scope.permission()));
                    scopes.computeIfAbsent(invoke.action(), ignored -> new HashSet<>()).addAll(subjects);
                    if (scopes.size() > MAX_ACTIONS || scopes.values().stream().mapToInt(Set::size).sum() > MAX_SCOPES) {
                        throw new IllegalArgumentException("Client action manifest exceeds its budget");
                    }
                    type = new ValueType(SFMClientProgramActionDispatcher.resultSchema(descriptor), Optional.empty());
                } else throw new IllegalArgumentException("Unsupported client value expression");
                variables.put(key(let.variableName()), type);
                if (variables.size() > MAX_VARIABLES) throw new IllegalArgumentException("Too many client value bindings");
            } else if (statement instanceof IfStatement branch) {
                validateCondition(branch.condition(), variables);
                link(branch.trueBlock(), new HashMap<>(variables), actions, permissions, scopes);
                link(branch.falseBlock(), new HashMap<>(variables), actions, permissions, scopes);
            }
        }
    }

    private static void validateCondition(BoolExpr condition, Map<String, ValueType> variables) {
        if (condition instanceof BoolClientValueEquals comparison) require(variables, comparison.variable());
        else if (condition instanceof BoolParen paren) validateCondition(paren.inner(), variables);
        else if (condition instanceof BoolNegation not) validateCondition(not.inner(), variables);
        else if (condition instanceof BoolConjunction both) { validateCondition(both.left(), variables); validateCondition(both.right(), variables); }
        else if (condition instanceof BoolDisjunction either) { validateCondition(either.left(), variables); validateCondition(either.right(), variables); }
    }

    static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private static ValueType require(Map<String, ValueType> variables, String name) {
        ValueType value = variables.get(key(name));
        if (value == null) throw new IllegalArgumentException("Unbound client value: " + name);
        return value;
    }

    public static SFMValue field(SFMValue value, String name) {
        return value instanceof SFMValue.ObjectValue object
                ? object.fields().getOrDefault(name, SFMValue.nullValue()) : SFMValue.nullValue();
    }

    private static SFMValueSchema fieldSchema(SFMValueSchema source, String name) {
        // Arbitrary inbox payloads retain their open shape. Runtime FIELD returns null for non-objects or absent keys.
        if (source instanceof SFMValueSchema.AnySchema) return SFMValueSchema.any();
        if (source instanceof SFMValueSchema.LiteralSchema literal) {
            if (!(literal.expected() instanceof SFMValue.ObjectValue object) || !object.fields().containsKey(name)) {
                throw new IllegalArgumentException("Field is absent from client value: " + name);
            }
            return SFMValueSchema.literal(object.fields().get(name));
        }
        if (source instanceof SFMValueSchema.OptionalSchema optional) {
            return SFMValueSchema.optional(fieldSchema(optional.value(), name));
        }
        if (source instanceof SFMValueSchema.ObjectSchema object) {
            SFMValueSchema.Field field = object.fields().get(name);
            if (field != null) return field.required() ? field.schema() : SFMValueSchema.optional(field.schema());
            if (object.allowExtraFields()) return SFMValueSchema.any();
        }
        throw new IllegalArgumentException("Field is not declared by the client value schema: " + name);
    }
}
