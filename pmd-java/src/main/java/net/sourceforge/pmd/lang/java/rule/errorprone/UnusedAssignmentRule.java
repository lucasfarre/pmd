/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule.errorprone;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAnnotationTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTAnyTypeBodyDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTAnyTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTAssignmentOperator;
import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTBlockStatement;
import net.sourceforge.pmd.lang.java.ast.ASTBreakStatement;
import net.sourceforge.pmd.lang.java.ast.ASTCatchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTContinueStatement;
import net.sourceforge.pmd.lang.java.ast.ASTDoStatement;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFinallyStatement;
import net.sourceforge.pmd.lang.java.ast.ASTForInit;
import net.sourceforge.pmd.lang.java.ast.ASTForStatement;
import net.sourceforge.pmd.lang.java.ast.ASTForUpdate;
import net.sourceforge.pmd.lang.java.ast.ASTIfStatement;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTLabeledStatement;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPostfixExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPreDecrementExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPreIncrementExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryPrefix;
import net.sourceforge.pmd.lang.java.ast.ASTPrimarySuffix;
import net.sourceforge.pmd.lang.java.ast.ASTResourceSpecification;
import net.sourceforge.pmd.lang.java.ast.ASTReturnStatement;
import net.sourceforge.pmd.lang.java.ast.ASTStatement;
import net.sourceforge.pmd.lang.java.ast.ASTStatementExpression;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchExpression;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLabel;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLabeledRule;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTThrowStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTryStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.lang.java.ast.ASTVariableInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTWhileStatement;
import net.sourceforge.pmd.lang.java.ast.ASTYieldStatement;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.ast.JavaParserVisitorAdapter;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.symboltable.ClassScope;
import net.sourceforge.pmd.lang.java.symboltable.VariableNameDeclaration;
import net.sourceforge.pmd.lang.symboltable.Scope;

public class UnusedAssignmentRule extends AbstractJavaRule {

    /*
        TODO
           * constructors + initializers
           * labels on arbitrary statements
           * foreach var should be reassigned from one iter to another
           * test local class/anonymous class

        DONE
           * conditionals
           * loops
           * switch
           * loop labels
           * try/catch/finally
           * lambdas


     */

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        for (JavaNode child : node.children()) {
            if (child instanceof ASTTypeDeclaration) {

                ASTAnyTypeDeclaration typeDecl = (ASTAnyTypeDeclaration) child.getChild(child.getNumChildren() - 1);
                GlobalAlgoState result = new GlobalAlgoState();
                typeDecl.jjtAccept(ReachingDefsVisitor.ONLY_LOCALS, new AlgoState(result));

                reportFinished(result, (RuleContext) data);
            }
        }

        return data;
    }

    private void reportFinished(GlobalAlgoState result, RuleContext ruleCtx) {
        if (result.usedAssignments.size() < result.allAssignments.size()) {
            HashSet<AssignmentEntry> unused = new HashSet<>(result.allAssignments);
            unused.removeAll(result.usedAssignments);

            for (AssignmentEntry entry : unused) {
                boolean isField = entry.var.getNode().getScope() instanceof ClassScope;

                Set<AssignmentEntry> killers = result.killRecord.get(entry);
                if (killers == null || killers.isEmpty()) {
                    if (isField) {
                        // assignments to fields don't really go out of scope
                        continue;
                    }
                    addViolation(ruleCtx, entry.rhs, new Object[] {entry.var.getImage(), "goes out of scope"});
                } else if (killers.size() == 1) {
                    AssignmentEntry k = killers.iterator().next();
                    addViolation(ruleCtx, entry.rhs, new Object[] {entry.var.getImage(),
                                                                   "overwritten on line " + k.rhs.getBeginLine()});
                } else {
                    addViolation(ruleCtx, entry.rhs, new Object[] {entry.var.getImage(), joinLines("overwritten on lines ", killers)});
                }
            }
        }
    }

    private static String joinLines(String prefix, Set<AssignmentEntry> killers) {
        StringBuilder sb = new StringBuilder(prefix);
        ArrayList<AssignmentEntry> sorted = new ArrayList<>(killers);
        Collections.sort(sorted, new Comparator<AssignmentEntry>() {
            @Override
            public int compare(AssignmentEntry o1, AssignmentEntry o2) {
                return Integer.compare(o1.rhs.getBeginLine(), o2.rhs.getBeginLine());
            }
        });

        sb.append(sorted.get(0).rhs.getBeginLine());
        for (int i = 1; i < sorted.size() - 1; i++) {
            sb.append(", ").append(sorted.get(i).rhs.getBeginLine());
        }
        sb.append(" and ").append(sorted.get(sorted.size() - 1).rhs.getBeginLine());

        return sb.toString();
    }

    private static class ReachingDefsVisitor extends JavaParserVisitorAdapter {


        static final ReachingDefsVisitor ONLY_LOCALS = new ReachingDefsVisitor(null);

        // This analysis can be trivially used to check for unused variables,
        // in the absence of global variable usage pre-resolution (which in
        // 7.0 is not implemented yet and maybe won't be).
        // See reverted commit somewhere in the PR

        // The class scope for the "this" reference, used to find fields
        // of this class
        // null if we're not processing instance/static initializers,
        // so in methods we don't care about fields
        // If not null, fields are effectively treated as locals
        private final ClassScope enclosingClassScope;

        private ReachingDefsVisitor(ClassScope scope) {
            this.enclosingClassScope = scope;
        }


        // following deals with control flow structures

        @Override
        public Object visit(JavaNode node, Object data) {

            for (JavaNode child : node.children()) {
                // each output is passed as input to the next (most relevant for blocks)
                data = child.jjtAccept(this, data);
            }

            return data;
        }

        @Override
        public Object visit(ASTBlock node, final Object data) {
            // variables local to a loop iteration must be killed before the
            // next iteration

            AlgoState state = (AlgoState) data;
            Set<VariableNameDeclaration> localsToKill = new HashSet<>();

            for (JavaNode child : node.children()) {
                // each output is passed as input to the next (most relevant for blocks)
                state = acceptOpt(child, state);
                if (child instanceof ASTBlockStatement
                    && child.getChild(0) instanceof ASTLocalVariableDeclaration) {
                    ASTLocalVariableDeclaration local = (ASTLocalVariableDeclaration) child.getChild(0);
                    for (ASTVariableDeclaratorId id : local) {
                        localsToKill.add(id.getNameDeclaration());
                    }
                }
            }

            for (VariableNameDeclaration var : localsToKill) {
                state.undef(var);
            }

            return state;
        }

        @Override
        public Object visit(ASTSwitchStatement node, Object data) {
            return processSwitch(node, (AlgoState) data, node.getTestedExpression());
        }

        @Override
        public Object visit(ASTSwitchExpression node, Object data) {
            return processSwitch(node, (AlgoState) data, node.getChild(0));
        }

        private AlgoState processSwitch(JavaNode switchLike, AlgoState data, JavaNode testedExpr) {
            GlobalAlgoState global = data.global;
            AlgoState before = acceptOpt(testedExpr, data);

            global.breakTargets.push(before.fork());

            AlgoState current = before;
            for (int i = 1; i < switchLike.getNumChildren(); i++) {
                JavaNode child = switchLike.getChild(i);
                if (child instanceof ASTSwitchLabel) {
                    current = before.fork().absorb(current);
                } else if (child instanceof ASTSwitchLabeledRule) {
                    current = acceptOpt(child.getChild(1), before.fork());
                    current = global.breakTargets.doBreak(current, null); // process this as if it was followed by a break
                } else {
                    // statement in a regular fallthrough switch block
                    current = acceptOpt(child, current);
                }
            }

            before = global.breakTargets.pop();

            // join with the last state, which is the exit point of the
            // switch, if it's not closed by a break;
            return before.absorb(current);
        }

        @Override
        public Object visit(ASTIfStatement node, Object data) {
            AlgoState before = acceptOpt(node.getCondition(), (AlgoState) data);

            AlgoState thenState = acceptOpt(node.getThenBranch(), before.fork());
            AlgoState elseState = node.hasElse() ? acceptOpt(node.getElseBranch(), before)
                                                 : before;

            return elseState.absorb(thenState);
        }

        @Override
        public Object visit(ASTTryStatement node, Object data) {
            final AlgoState before = (AlgoState) data;
            ASTFinallyStatement finallyClause = node.getFinallyClause();

            /*
                <before>
                try (<resources>) {
                    <body>
                } catch (IOException e) {
                    <catch>
                } finally {
                    <finally>
                }
                <end>

                There is a path      <before> -> <resources> -> <body> -> <finally> -> <end>
                and for each catch,  <before> -> <catch> -> <finally> -> <end>

                Except that abrupt completion before the <finally> jumps
                to the <finally> and completes abruptly for the same
                reason (if the <finally> completes normally), which
                means it doesn't go to <end>
             */

            if (finallyClause != null) {
                before.myFinally = before.forkEmpty();
            }

            ASTResourceSpecification resources = node.getFirstChildOfType(ASTResourceSpecification.class);

            AlgoState bodyState = acceptOpt(resources, before.fork());
            bodyState = acceptOpt(node.getBody(), bodyState);

            AlgoState exceptionalState = null;
            for (ASTCatchStatement catchClause : node.getCatchClauses()) {
                AlgoState current = acceptOpt(catchClause, before.fork());
                exceptionalState = current.absorb(exceptionalState);
            }

            AlgoState finalState;
            finalState = bodyState.absorb(exceptionalState);
            if (finallyClause != null) {
                // this represents the finally clause when it was entered
                // because of abrupt completion
                // since we don't know when it terminated we must join it with before
                AlgoState abruptFinally = before.myFinally.absorb(before);
                acceptOpt(finallyClause, abruptFinally);
                before.myFinally = null;

                // this is the normal finally
                finalState = acceptOpt(finallyClause, finalState);
            }
            return finalState;
        }

        @Override
        public Object visit(ASTLambdaExpression node, Object data) {
            // Lambda expression have control flow that is separate from the method
            // So we fork the context, but don't join it

            // Reaching definitions of the enclosing context still reach in the lambda
            // Since those definitions are [effectively] final, they actually can't be
            // killed, but they can be used in the lambda

            AlgoState before = (AlgoState) data;

            JavaNode lambdaBody = node.getChild(node.getNumChildren() - 1);
            // if it's an expression, then no assignments may occur in it,
            // but it can still use some variables of the context
            acceptOpt(lambdaBody, before.forkCapturingNonLocal());
            return before;
        }

        @Override
        public Object visit(ASTWhileStatement node, Object data) {
            return handleLoop(node, (AlgoState) data, null, node.getCondition(), null, node.getBody(), true);
        }

        @Override
        public Object visit(ASTDoStatement node, Object data) {
            return handleLoop(node, (AlgoState) data, null, node.getCondition(), null, node.getBody(), false);
        }

        @Override
        public Object visit(ASTForStatement node, Object data) {
            ASTStatement body = node.getBody();
            if (node.isForeach()) {
                // the iterable expression
                JavaNode init = node.getChild(1);
                return handleLoop(node, (AlgoState) data, init, null, null, body, true);
            } else {
                ASTForInit init = node.getFirstChildOfType(ASTForInit.class);
                ASTExpression cond = node.getCondition();
                ASTForUpdate update = node.getFirstChildOfType(ASTForUpdate.class);
                return handleLoop(node, (AlgoState) data, init, cond, update, body, true);
            }
        }


        private AlgoState handleLoop(JavaNode loop,
                                     AlgoState before,
                                     JavaNode init,
                                     JavaNode cond,
                                     JavaNode update,
                                     JavaNode body,
                                     boolean checkFirstIter) {
            final GlobalAlgoState globalState = before.global;

            // perform a few "iterations", to make sure that assignments in
            // the body can affect themselves in the next iteration, and
            // that they affect the condition, etc

            before = acceptOpt(init, before);
            if (checkFirstIter) { // false for do-while
                before = acceptOpt(cond, before);
            }

            AlgoState breakTarget = before.forkEmpty();
            AlgoState continueTarget = before.forkEmpty();

            pushTargets(loop, breakTarget, continueTarget);

            AlgoState iter = acceptOpt(body, before.fork());
            // make the defs of the body reach the other parts of the loop,
            // including itself
            iter = acceptOpt(update, iter);
            iter = acceptOpt(cond, iter);
            iter = acceptOpt(body, iter);


            breakTarget = globalState.breakTargets.peek();
            continueTarget = globalState.continueTargets.peek();
            if (!continueTarget.reachingDefs.isEmpty()) {
                // make assignments before a continue reach the other parts of the loop
                continueTarget = acceptOpt(cond, continueTarget);
                continueTarget = acceptOpt(body, continueTarget);
                continueTarget = acceptOpt(update, continueTarget);
            }

            AlgoState result = popTargets(loop, breakTarget, continueTarget);
            result = result.absorb(iter);
            if (checkFirstIter) {
                // if the first iteration is checked,
                // then it could be false on the first try, meaning
                // the definitions before the loop reach after too
                result = result.absorb(before);
            }

            return result;
        }

        private void pushTargets(JavaNode loop, AlgoState breakTarget, AlgoState continueTarget) {
            GlobalAlgoState globalState = breakTarget.global;
            globalState.breakTargets.unnamedTargets.push(breakTarget);
            globalState.continueTargets.unnamedTargets.push(continueTarget);

            Node parent = loop.getNthParent(2);
            while (parent instanceof ASTLabeledStatement) {
                String label = parent.getImage();
                globalState.breakTargets.namedTargets.put(label, breakTarget);
                globalState.continueTargets.namedTargets.put(label, continueTarget);
                parent = parent.getNthParent(2);
            }
        }

        private AlgoState popTargets(JavaNode loop, AlgoState breakTarget, AlgoState continueTarget) {
            GlobalAlgoState globalState = breakTarget.global;
            globalState.breakTargets.unnamedTargets.pop();
            globalState.continueTargets.unnamedTargets.pop();

            AlgoState total = breakTarget.absorb(continueTarget);

            Node parent = loop.getNthParent(2);
            while (parent instanceof ASTLabeledStatement) {
                String label = parent.getImage();
                total = total.absorb(globalState.breakTargets.namedTargets.remove(label));
                total = total.absorb(globalState.continueTargets.namedTargets.remove(label));
                parent = parent.getNthParent(2);
            }
            return total;
        }

        private AlgoState acceptOpt(JavaNode node, AlgoState before) {
            return node == null ? before : (AlgoState) node.jjtAccept(this, before);
        }

        @Override
        public Object visit(ASTContinueStatement node, Object data) {
            AlgoState state = (AlgoState) data;
            return state.global.continueTargets.doBreak(state, node.getImage());
        }

        @Override
        public Object visit(ASTBreakStatement node, Object data) {
            AlgoState state = (AlgoState) data;
            return state.global.breakTargets.doBreak(state, node.getImage());
        }

        @Override
        public Object visit(ASTYieldStatement node, Object data) {
            super.visit(node, data); // visit expression

            AlgoState state = (AlgoState) data;
            // treat as break, ie abrupt completion + link reaching defs to outer context
            return state.global.breakTargets.doBreak(state, null);
        }


        // both of those exit the scope of the method/ctor, so their assignments go dead

        @Override
        public Object visit(ASTThrowStatement node, Object data) {
            super.visit(node, data);
            return ((AlgoState) data).abruptCompletion(null);
        }

        @Override
        public Object visit(ASTReturnStatement node, Object data) {
            super.visit(node, data);
            return ((AlgoState) data).abruptCompletion(null);
        }

        // following deals with assignment


        @Override
        public Object visit(ASTVariableDeclarator node, Object data) {
            VariableNameDeclaration var = node.getVariableId().getNameDeclaration();
            ASTVariableInitializer rhs = node.getInitializer();
            if (rhs != null) {
                rhs.jjtAccept(this, data);
                ((AlgoState) data).assign(var, rhs);
            }
            return data;
        }


        @Override
        public Object visit(ASTExpression node, Object data) {
            return checkAssignment(node, data);
        }

        @Override
        public Object visit(ASTStatementExpression node, Object data) {
            return checkAssignment(node, data);
        }

        public Object checkAssignment(JavaNode node, Object data) {
            AlgoState result = (AlgoState) data;
            if (node.getNumChildren() == 3) {
                // assignment
                assert node.getChild(1) instanceof ASTAssignmentOperator;

                // visit the rhs as it is evaluated before
                JavaNode rhs = node.getChild(2);
                result = acceptOpt(rhs, result);

                VariableNameDeclaration lhsVar = getLhsVar(node.getChild(0), true);
                if (lhsVar != null) {
                    // in that case lhs is a normal variable (array access not supported)

                    if (node.getChild(1).getImage().length() >= 2) {
                        // compound assignment, to use BEFORE assigning
                        result.use(lhsVar);
                    }

                    result.assign(lhsVar, rhs);
                } else {
                    result = acceptOpt(node.getChild(0), result);
                }
                return result;
            } else {
                return visit(node, data);
            }
        }

        @Override
        public Object visit(ASTPreDecrementExpression node, Object data) {
            return checkIncOrDecrement(node, (AlgoState) data);
        }

        @Override
        public Object visit(ASTPreIncrementExpression node, Object data) {
            return checkIncOrDecrement(node, (AlgoState) data);
        }

        @Override
        public Object visit(ASTPostfixExpression node, Object data) {
            return checkIncOrDecrement(node, (AlgoState) data);
        }

        private AlgoState checkIncOrDecrement(JavaNode unary, AlgoState data) {
            VariableNameDeclaration var = getLhsVar(unary.getChild(0), true);
            if (var != null) {
                data.use(var);
                data.assign(var, unary);
            }
            return data;
        }

        // variable usage

        @Override
        public Object visit(ASTPrimaryExpression node, Object data) {
            super.visit(node, data); // visit subexpressions

            VariableNameDeclaration var = getLhsVar(node, false);
            if (var != null) {
                ((AlgoState) data).use(var);
            }
            return data;
        }

        private VariableNameDeclaration getLhsVar(JavaNode primary, boolean inLhs) {
            if (primary instanceof ASTPrimaryExpression) {
                ASTPrimaryPrefix prefix = (ASTPrimaryPrefix) primary.getChild(0);

                // todo
                //   this.x = 2;

                //                if (prefix.usesThisModifier()) {
                //                    if (primary.getNumChildren() < 2) {
                //                        return null;
                //                    }
                //                    ASTPrimarySuffix suffix = (ASTPrimarySuffix) primary.getChild(1);
                //                    if (suffix.isArguments() || suffix.isArrayDereference() || suffix.getImage() == null) {
                //                        return null;
                //                    }
                //                    return findVar(primary.getScope(), true, firstIdent(suffix.getImage()));
                //                } else
                {
                    if (prefix.getNumChildren() > 0 && (prefix.getChild(0) instanceof ASTName)) {
                        String prefixImage = prefix.getChild(0).getImage();
                        String varname = identOf(inLhs, prefixImage);
                        if (primary.getNumChildren() > 1 && !inLhs) {
                            ASTPrimarySuffix suffix = (ASTPrimarySuffix) primary.getChild(1);
                            if (suffix.isArguments()) {
                                // then the prefix has the method name
                                varname = methodLhsName(prefixImage);
                            }
                        }
                        return findVar(prefix.getScope(), varname);
                    }
                }
            }

            return null;
        }

        private static String identOf(boolean inLhs, String str) {
            int i = str.indexOf('.');
            if (i < 0) {
                return str;
            } else if (inLhs) {
                // a qualified name in LHS, so
                // the assignment doesn't assign the variable but one of its fields
                return null;
            }
            return str.substring(0, i);
        }

        private static String methodLhsName(String name) {
            int i = name.indexOf('.');
            return i < 0 ? null // no lhs, the name is just the method name
                         : name.substring(0, i);
        }

        private VariableNameDeclaration findVar(Scope scope, String name) {
            if (name == null) {
                return null;
            }

            while (scope != null) {
                for (VariableNameDeclaration decl : scope.getDeclarations(VariableNameDeclaration.class).keySet()) {
                    if (decl.getImage().equals(name)) {
                        if (scope instanceof ClassScope && scope != enclosingClassScope) {
                            // don't handle fields
                            return null;
                        }
                        return decl;
                    }
                }

                scope = scope.getParent();
            }

            return null;
        }


        // ctor/initializer handling

        @Override
        public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
            return visitType(node, data);
        }

        @Override
        public Object visit(ASTEnumDeclaration node, Object data) {
            return visitType(node, data);
        }

        @Override
        public Object visit(ASTAnnotationTypeDeclaration node, Object data) {
            return visitType(node, data);
        }

        private Object visitType(ASTAnyTypeDeclaration type, Object data) {
            AlgoState prev = (AlgoState) data;
            processInitializers(type, prev);

            for (ASTAnyTypeBodyDeclaration decl : type.getDeclarations()) {
                JavaNode d = decl.getDeclarationNode();
                if (d instanceof ASTMethodDeclaration) {
                    ONLY_LOCALS.acceptOpt(d, prev.forkCapturingNonLocal());
                } else if (d instanceof ASTAnyTypeDeclaration) {
                    visitType((ASTAnyTypeDeclaration) d, prev.forkEmptyNonLocal());
                }
            }

            return data; // type doesn't contribute anything to the enclosing control flow
        }

        // todo anon class


        public static void processInitializers(ASTAnyTypeDeclaration klass,
                                               AlgoState beforeLocal) {

            ReachingDefsVisitor visitor = new ReachingDefsVisitor((ClassScope) klass.getScope());

            // All field initializers + instance initializers
            AlgoState ctorHeader = beforeLocal.forkCapturingNonLocal();
            // All static field initializers + static initializers
            AlgoState staticInit = beforeLocal.forkEmptyNonLocal();

            List<ASTConstructorDeclaration> ctors = new ArrayList<>();

            for (ASTAnyTypeBodyDeclaration declaration : klass.getDeclarations()) {
                JavaNode node = declaration.getDeclarationNode();

                final boolean isStatic;
                if (node instanceof ASTFieldDeclaration) {
                    isStatic = ((ASTFieldDeclaration) node).isStatic();
                } else if (node instanceof ASTInitializer) {
                    isStatic = ((ASTInitializer) node).isStatic();
                } else if (node instanceof ASTConstructorDeclaration) {
                    ctors.add((ASTConstructorDeclaration) node);
                    continue;
                } else {
                    continue;
                }

                if (isStatic) {
                    staticInit = visitor.acceptOpt(node, staticInit);
                } else {
                    ctorHeader = visitor.acceptOpt(node, ctorHeader);
                }
            }


            AlgoState ctorEndState = ctors.isEmpty() ? ctorHeader : null;
            for (ASTConstructorDeclaration ctor : ctors) {
                AlgoState state = visitor.acceptOpt(ctor, ctorHeader.forkCapturingNonLocal());
                ctorEndState = ctorEndState == null ? state : ctorEndState.absorb(state);
            }

            // assignments that reach the end of any constructor must
            // be considered used
            for (VariableNameDeclaration field : visitor.enclosingClassScope.getVariableDeclarations().keySet()) {
                ctorEndState.use(field);
            }
        }
    }

    private static class GlobalAlgoState {

        final Set<AssignmentEntry> allAssignments;
        final Set<AssignmentEntry> usedAssignments;
        final Map<AssignmentEntry, Set<AssignmentEntry>> killRecord;

        final TargetStack breakTargets = new TargetStack();
        // continue jumps to the condition check, while break jumps to after the loop
        final TargetStack continueTargets = new TargetStack();

        private GlobalAlgoState(Set<AssignmentEntry> allAssignments,
                                Set<AssignmentEntry> usedAssignments,
                                Map<AssignmentEntry, Set<AssignmentEntry>> killRecord) {
            this.allAssignments = allAssignments;
            this.usedAssignments = usedAssignments;
            this.killRecord = killRecord;
        }

        private GlobalAlgoState() {
            this(new HashSet<AssignmentEntry>(),
                 new HashSet<AssignmentEntry>(),
                 new HashMap<AssignmentEntry, Set<AssignmentEntry>>());
        }
    }

    private static class AlgoState {

        final AlgoState parent;

        final GlobalAlgoState global;

        // Map of var -> reaching(var)
        final Map<VariableNameDeclaration, Set<AssignmentEntry>> reachingDefs;

        AlgoState myFinally = null;

        private AlgoState(GlobalAlgoState global) {
            this(null, global, new HashMap<VariableNameDeclaration, Set<AssignmentEntry>>());
        }

        private AlgoState(AlgoState parent,
                          GlobalAlgoState global,
                          Map<VariableNameDeclaration, Set<AssignmentEntry>> reachingDefs) {
            this.parent = parent;
            this.global = global;
            this.reachingDefs = reachingDefs;
        }

        void assign(VariableNameDeclaration var, JavaNode rhs) {
            AssignmentEntry entry = new AssignmentEntry(var, rhs);
            // kills the previous value
            Set<AssignmentEntry> killed = reachingDefs.put(var, Collections.singleton(entry));
            if (killed != null) {
                for (AssignmentEntry k : killed) {
                    // computeIfAbsent
                    Set<AssignmentEntry> killers = global.killRecord.get(k);
                    if (killers == null) {
                        killers = new HashSet<>(1);
                        global.killRecord.put(k, killers);
                    }
                    killers.add(entry);
                }
            }
            global.allAssignments.add(entry);
        }

        void use(VariableNameDeclaration var) {
            Set<AssignmentEntry> reaching = reachingDefs.get(var);
            // may be null for implicit assignments, like method parameter
            if (reaching != null) {
                global.usedAssignments.addAll(reaching);
            }
        }

        void undef(VariableNameDeclaration var) {
            reachingDefs.remove(var);
        }

        AlgoState fork() {
            return doFork(this, new HashMap<>(this.reachingDefs));
        }

        AlgoState forkEmpty() {
            return doFork(this, new HashMap<VariableNameDeclaration, Set<AssignmentEntry>>());
        }

        AlgoState forkEmptyNonLocal() {
            return doFork(null, new HashMap<VariableNameDeclaration, Set<AssignmentEntry>>());
        }

        AlgoState forkCapturingNonLocal() {
            // has no parent, so that in case of abrupt completion (return inside a lambda),
            // enclosing finallies are not called
            return doFork(null, new HashMap<>(this.reachingDefs));
        }

        private AlgoState doFork(AlgoState parent, Map<VariableNameDeclaration, Set<AssignmentEntry>> reaching) {
            return new AlgoState(parent, this.global, reaching);
        }

        AlgoState abruptCompletion(AlgoState target) {
            // if target == null then this will unwind all the parents
            AlgoState parent = this;
            while (parent != target && parent != null) {
                if (parent.myFinally != null) {
                    parent.myFinally.absorb(this);
                }
                parent = parent.parent;
            }

            this.reachingDefs.clear();
            return this;
        }


        AlgoState absorb(AlgoState sub) {
            // Merge reaching des of the other scope into this
            if (sub == this || sub == null || sub.reachingDefs.isEmpty()) {
                return this;
            }

            for (VariableNameDeclaration var : this.reachingDefs.keySet()) {
                Set<AssignmentEntry> myAssignments = this.reachingDefs.get(var);
                Set<AssignmentEntry> subScopeAssignments = sub.reachingDefs.get(var);
                if (subScopeAssignments == null) {
                    continue;
                }
                joinSets(var, myAssignments, subScopeAssignments);
            }

            for (VariableNameDeclaration var : sub.reachingDefs.keySet()) {
                Set<AssignmentEntry> subScopeAssignments = sub.reachingDefs.get(var);
                Set<AssignmentEntry> myAssignments = this.reachingDefs.get(var);
                if (myAssignments == null) {
                    this.reachingDefs.put(var, subScopeAssignments);
                    continue;
                }
                joinSets(var, myAssignments, subScopeAssignments);
            }

            return this;
        }

        private void joinSets(VariableNameDeclaration var, Set<AssignmentEntry> set1, Set<AssignmentEntry> set2) {
            Set<AssignmentEntry> newReaching = new HashSet<>(set1.size() + set2.size());
            newReaching.addAll(set2);
            newReaching.addAll(set1);
            this.reachingDefs.put(var, newReaching);
        }

        @Override
        public String toString() {
            return reachingDefs.toString();
        }
    }

    static class TargetStack {

        final Deque<AlgoState> unnamedTargets = new ArrayDeque<>();
        final Map<String, AlgoState> namedTargets = new HashMap<>();


        void push(AlgoState state) {
            unnamedTargets.push(state);
        }

        AlgoState pop() {
            return unnamedTargets.pop();
        }

        AlgoState peek() {
            return unnamedTargets.getFirst();
        }

        AlgoState doBreak(AlgoState data,/* nullable */ String label) {
            // basically, reaching defs at the point of the break
            // also reach after the break (wherever it lands)
            AlgoState target;
            if (label == null) {
                target = unnamedTargets.getFirst();
            } else {
                target = namedTargets.get(label);
            }

            if (target != null) { // otherwise CT error
                target.absorb(data);
            }
            return data.abruptCompletion(target);
        }
    }

    static class AssignmentEntry {

        final VariableNameDeclaration var;
        final JavaNode rhs;

        AssignmentEntry(VariableNameDeclaration var, JavaNode rhs) {
            this.var = var;
            this.rhs = rhs;
        }

        @Override
        public String toString() {
            return var.getImage() + " := " + rhs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AssignmentEntry that = (AssignmentEntry) o;
            return Objects.equals(rhs, that.rhs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rhs);
        }
    }
}
