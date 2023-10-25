package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

class SymbolTableVisitor extends AJmmVisitor<Object, Object> {
    private final JmmSymbolTable table;

    public SymbolTableVisitor(JmmSymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::visitOther);
        addVisit("PackageDeclaration", this::visitPackage);
        addVisit("ImportStatement", this::visitImport);
        addVisit("ClassDeclaration", this::visitClass);
        addVisit("ParentClass", this::visitParentClass);
        addVisit("MethodDeclaration", this::visitMethod);
        addVisit("ConstructorDeclaration", this::visitMethod);
        addVisit("ParameterList", this::visitParameters);
        addVisit("VariableDeclaration", this::visitVariable);
        addVisit("PrimitiveType", this::visitType);
        addVisit("VoidType", this::visitType);
        addVisit("ComplexType", this::visitType);
        addVisit("ArrayType", this::visitType);
        addVisit("ForEachStatement", this::visitForEach);
    }

    private Object visitOther(JmmNode node, Object context) {
        for (var child : node.getChildren())
            visit(child, context);

        return context;
    }

    private Object visitClass(JmmNode node, Object context) {
        table.setClassName(node.get("className"));

        for (var child : node.getChildren())
            visit(child, context);

        return context;
    }

    private Object visitParentClass(JmmNode node, Object context) {
        StringBuilder superNameBuilder = new StringBuilder();

        var parentPackage = node.getObjectAsList("parentPackage", String.class);

        for (var p : parentPackage)
            superNameBuilder.append(p).append('.');
        superNameBuilder.append(node.get("parentClass"));

        table.setSuperName(superNameBuilder.toString());

        return context;
    }

    private Object visitPackage(JmmNode node, Object context) {
        table.setPackageName(node.getObjectAsList("packagePath", String.class).stream().map(s -> s + '.').collect(Collectors.joining()) + node.get("packageName"));

        return context;
    }

    private Object visitImport(JmmNode node, Object context) {
        var prefix = String.join(".", node.getObjectAsList("classPackage", String.class));
        table.addImport(prefix.isEmpty() ? node.get("className") : prefix + "." + node.get("className"));

        return context;
    }

    private Method visitMethod(JmmNode node, Object context) {
        var method = new Method(
                node.getKind().equals("ConstructorDeclaration") ? "<constructor>" : node.get("methodName"),
                new Type("void", false));

        table.addMethod(method);

        method.setModifiers(new TreeSet<>(node.getObjectAsList("modifiers", String.class)));

        for (var child : node.getChildren())
            visit(child, method);

        return method;
    }

    private Type visitType(JmmNode node, Object context) {
        Type type;

        // FIXME: THIS CODE WAS DONE AT 5AM

        var typeId = node.getOptional("id");

        if (typeId.isPresent()) {
            var realTypeId = typeId.get(); // TODO: ew

            try { // Complex type
                var typePrefix = node.getObjectAsList("typePrefix", String.class);

                var actualType = typePrefix.stream().map(s -> s + '.').collect(Collectors.joining()) + realTypeId;

                type = new Type(actualType, false);
            } catch (Exception e) { // Simple type
                type = new Type(realTypeId, false);
            }
        } else if (node.getNumChildren() == 1) // Array Type
            type = new Type(((Type) visit(node.getJmmChild(0), context)).getName(), true);
        else // Void Type
            type = new Type("void", false);

        if (node.getJmmParent().getKind().equals("MethodDeclaration") && context instanceof Method)
            ((Method) context).setReturnType(type);

        node.put("type", type.print());

        return type;
    }

    private List<Symbol> visitParameters(JmmNode node, Object context) {
        assert context instanceof Method;

        var params = node.getObjectAsList("argName");

        Method method = (Method) context;
        for (int i = 0; i < node.getChildren().size(); ++i) {
            Type type = (Type) visit(node.getJmmChild(i), method.getParameters());

            Symbol parameter = new Symbol(type, params.get(i).toString());

            method.getParameters().add(parameter);
        }

        return method.getParameters();
    }

    private Symbol visitVariable(JmmNode node, Object context) {
        var symbol = new Symbol((Type) visit(node.getJmmChild(0)), node.get("id"));

        if (context instanceof Method) ((Method) context).getLocalVariables().add(symbol);
        else table.addField(symbol);

        return symbol;
    }

    private Object visitForEach(JmmNode node, Object context) {
        assert context instanceof Method;

        var type = (Type) visit(node.getJmmChild(0), context);
        var id = node.get("id");

        var symbol = new Symbol(type, id);
        ((Method) context).getLocalVariables().add(symbol);

        for (var child : node.getChildren())
            visit(child, context);

        return context;
    }
}
