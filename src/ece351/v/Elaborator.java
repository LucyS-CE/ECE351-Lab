/* *********************************************************************
 * ECE351 
 * Department of Electrical and Computer Engineering 
 * University of Waterloo 
 * Term: Fall 2021 (1219)
 *
 * The base version of this file is the intellectual property of the
 * University of Waterloo. Redistribution is prohibited.
 *
 * By pushing changes to this file I affirm that I am the author of
 * all changes. I affirm that I have complied with the course
 * collaboration policy and have not plagiarized my work. 
 *
 * I understand that redistributing this file might expose me to
 * disciplinary action under UW Policy 71. I understand that Policy 71
 * allows for retroactive modification of my final grade in a course.
 * For example, if I post my solutions to these labs on GitHub after I
 * finish ECE351, and a future student plagiarizes them, then I too
 * could be found guilty of plagiarism. Consequently, my final grade
 * in ECE351 could be retroactively lowered. This might require that I
 * repeat ECE351, which in turn might delay my graduation.
 *
 * https://uwaterloo.ca/secretariat-general-counsel/policies-procedures-guidelines/policy-71
 * 
 * ********************************************************************/

package ece351.v;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.List;
import java.util.ArrayList;

import org.parboiled.common.ImmutableList;

import ece351.common.ast.AndExpr;
import ece351.common.ast.AssignmentStatement;
import ece351.common.ast.ConstantExpr;
import ece351.common.ast.EqualExpr;
import ece351.common.ast.Expr;
import ece351.common.ast.NAndExpr;
import ece351.common.ast.NOrExpr;
import ece351.common.ast.NaryAndExpr;
import ece351.common.ast.NaryOrExpr;
import ece351.common.ast.NotExpr;
import ece351.common.ast.OrExpr;
import ece351.common.ast.Statement;
import ece351.common.ast.VarExpr;
import ece351.common.ast.XNOrExpr;
import ece351.common.ast.XOrExpr;
import ece351.common.visitor.PostOrderExprVisitor;
import ece351.util.CommandLine;
import ece351.v.ast.Architecture;
import ece351.v.ast.Component;
import ece351.v.ast.DesignUnit;
import ece351.v.ast.IfElseStatement;
import ece351.v.ast.Process;
import ece351.v.ast.VProgram;

/**
 * Inlines logic in components to architecture body.
 */
public final class Elaborator extends PostOrderExprVisitor {

	private final Map<String, String> current_map = new LinkedHashMap<String, String>();
	
	public static void main(String[] args) {
		System.out.println(elaborate(args));
	}
	
	public static VProgram elaborate(final String[] args) {
		return elaborate(new CommandLine(args));
	}
	
	public static VProgram elaborate(final CommandLine c) {
        final VProgram program = DeSugarer.desugar(c);
        return elaborate(program);
	}
	
	public static VProgram elaborate(final VProgram program) {
		final Elaborator e = new Elaborator();
		return e.elaborateit(program);
	}

	private VProgram elaborateit(final VProgram root) {

		// our ASTs are immutable. so we cannot mutate root.
		// we need to construct a new AST that will be the return value.
		// it will be like the input (root), but different.
		VProgram result = new VProgram();
		int compCount = 0;

		// iterate over all of the designUnits in root.
		// for each one, construct a new architecture.
		// Architecture a = du.arch.varyComponents(ImmutableList.<Component>of());
		// this gives us a copy of the architecture with an empty list of components.
		// now we can build up this Architecture with new components.
		// In the elaborator, an architectures list of signals, and set of statements may change (grow)
		//populate dictionary/map	
		//add input signals, map to ports
		//add output signals, map to ports
		//add local signals, add to signal list of current designUnit						
		//loop through the statements in the architecture body		
		// make the appropriate variable substitutions for signal assignment statements
		// i.e., call changeStatementVars
		// make the appropriate variable substitutions for processes (sensitivity list, if/else body statements)
		// i.e., call expandProcessComponent
		// append this new architecture to result
		// TODO: longer code snippet
		// For each design unit, elaborate it.
		for (final DesignUnit du : root.designUnits) {
			Architecture originalArch = du.arch;
			ImmutableList<String> newSignals = originalArch.signals;
			// Start with the existing non-component statements
			ImmutableList<Statement> newStatements = originalArch.statements;

			// Iterate over all of the component instantiations in the architecture body
			for (final Component comp : originalArch.components) {
				// 1. Find the entity/architecture for this component
				DesignUnit componentDU = null;
				// 1.1. First look in the already elaborated result
				for (final DesignUnit searchDU : result.designUnits) {
					if (searchDU.entity.identifier.equals(comp.entityName)) {
						componentDU = searchDU;
						break;
					}
				}

				// 1.2. If not found yet, look in the original root
				if (componentDU == null) {
					for (final DesignUnit searchDU : root.designUnits) {
						if (searchDU.entity.identifier.equals(comp.entityName)) {
							componentDU = searchDU;
							break;
						}
					}
				}
				assert componentDU != null;

				// 2. Build the substitution map 
				current_map.clear();
				compCount++;
				final String prefix = "comp" + compCount + "_";

				// 2.1 Map component ports to architecture signals
				final List<String> ports = new ArrayList<String>(componentDU.entity.input);
				ports.addAll(componentDU.entity.output);
				final ImmutableList<String> entityPorts = ImmutableList.copyOf(ports);
				final Iterator<String> entityPortsIterator = entityPorts.iterator();
				final Iterator<String> componentPinsIterator = comp.signalList.iterator();
				while (entityPortsIterator.hasNext() && componentPinsIterator.hasNext()) {
					current_map.put(entityPortsIterator.next(), componentPinsIterator.next());
				}

				// 2.2. Map component local signals to new unique signals in the architecture
				for (final String localSignal : componentDU.arch.signals) {
					final String newSignalName = prefix + localSignal;
					newSignals = newSignals.append(newSignalName);
					current_map.put(localSignal, newSignalName);
				}

				// 3. Substitute and add statements from the component's architecture
				for (final Statement compStmt : componentDU.arch.statements) {
					if (compStmt instanceof AssignmentStatement) {
						newStatements = newStatements.append(changeStatementVars((AssignmentStatement) compStmt));

					} else if (compStmt instanceof Process) {
						newStatements = newStatements.append(expandProcessComponent((Process) compStmt));

					} else {
						newStatements = newStatements.append(compStmt);
					}

				}
			}

			// Create the new elaborated architecture with no components
			Architecture newArch = new Architecture(newStatements, ImmutableList.<Component>of(), newSignals,
					originalArch.entityName, originalArch.architectureName);

			// Add the new design unit (with the elaborated architecture) to the result program
			result = result.append(new DesignUnit(newArch, du.entity));
		}
		// throw new ece351.util.Todo351Exception();
		assert result.repOk();
		return result;
	}

	// recursively substitute variables in an expression only change Var
	// since the cannot changed override part below cannot be used while pass all tests, I have to implement this here
	private Expr substituteExpr(final Expr e) {
		if (e instanceof VarExpr) {
			final VarExpr v = (VarExpr) e;
			if (current_map.containsKey(v.identifier)) {
				return new VarExpr(current_map.get(v.identifier));
			}
			return v;
		}
		if (e instanceof ConstantExpr) {
			return e;
		}
		if (e instanceof NotExpr) {
			final NotExpr n = (NotExpr) e;
			final Expr child = substituteExpr(n.expr);
			return new NotExpr(child);
		}
		if (e instanceof AndExpr) {
			final AndExpr a = (AndExpr) e;
			return new AndExpr(substituteExpr(a.left), substituteExpr(a.right));
		}
		if (e instanceof OrExpr) {
			final OrExpr o = (OrExpr) e;
			return new OrExpr(substituteExpr(o.left), substituteExpr(o.right));
		}
		if (e instanceof XOrExpr) {
			final XOrExpr x = (XOrExpr) e;
			return new XOrExpr(substituteExpr(x.left), substituteExpr(x.right));
		}
		if (e instanceof EqualExpr) {
			final EqualExpr eq = (EqualExpr) e;
			return new EqualExpr(substituteExpr(eq.left), substituteExpr(eq.right));
		}
		if (e instanceof NAndExpr) {
			final NAndExpr n = (NAndExpr) e;
			return new NAndExpr(substituteExpr(n.left), substituteExpr(n.right));
		}
		if (e instanceof NOrExpr) {
			final NOrExpr n = (NOrExpr) e;
			return new NOrExpr(substituteExpr(n.left), substituteExpr(n.right));
		}
		if (e instanceof XNOrExpr) {
			final XNOrExpr x = (XNOrExpr) e;
			return new XNOrExpr(substituteExpr(x.left), substituteExpr(x.right));
		}
		if (e instanceof NaryAndExpr) {
			final NaryAndExpr n = (NaryAndExpr) e;
			ImmutableList<Expr> children = ImmutableList.of();
			for (final Expr c : n.children) {
				children = children.append(substituteExpr(c));
			}
			return new NaryAndExpr(children);
		}
		if (e instanceof NaryOrExpr) {
			final NaryOrExpr n = (NaryOrExpr) e;
			ImmutableList<Expr> children = ImmutableList.of();
			for (final Expr c : n.children) {
				children = children.append(substituteExpr(c));
			}
			return new NaryOrExpr(children);
		}

		// this should not be reached
		return e;
	}


	// you do not have to use these helper methods; we found them useful though
	private Process expandProcessComponent(final Process process) {
		// TODO: longer code snippet
		// 1. Replace signal names in the sensitivity list
		ImmutableList<String> newSensitivityList = ImmutableList.of();
		for (final String s : process.sensitivityList) {
			final String mapped = current_map.containsKey(s) ? current_map.get(s) : s;
			newSensitivityList = newSensitivityList.append(mapped);
		}

		// 2. Replace statements in the process
		ImmutableList<Statement> newSequentialStatements = ImmutableList.of();
		for (final Statement seqStmt : process.sequentialStatements) {
			if (seqStmt instanceof AssignmentStatement) {
				newSequentialStatements = newSequentialStatements
						.append(changeStatementVars((AssignmentStatement) seqStmt));
			} else if (seqStmt instanceof IfElseStatement) {
				newSequentialStatements = newSequentialStatements
						.append(changeIfVars((IfElseStatement) seqStmt));
			} else {
				// Other statements are added as is
				newSequentialStatements = newSequentialStatements.append(seqStmt);
			}
		}

		return new Process(newSequentialStatements, newSensitivityList);
// throw new ece351.util.Todo351Exception();
	}
	
	// you do not have to use these helper methods; we found them useful though
	private  IfElseStatement changeIfVars(final IfElseStatement s) {
		// TODO: longer code snippet
		final Expr newCondition = substituteExpr(s.condition);

		ImmutableList<AssignmentStatement> newIfBody = ImmutableList.of();
		for (final AssignmentStatement as : s.ifBody) {
			newIfBody = newIfBody.append(changeStatementVars(as));
		}

		ImmutableList<AssignmentStatement> newElseBody = ImmutableList.of();
		for (final AssignmentStatement as : s.elseBody) {
			newElseBody = newElseBody.append(changeStatementVars(as));
		}

		// IfElseStatement para: (elseBody, ifBody, condition)
		return new IfElseStatement(newElseBody, newIfBody, newCondition);
// throw new ece351.util.Todo351Exception();
	}

	// you do not have to use these helper methods; we found them useful though
	private AssignmentStatement changeStatementVars(final AssignmentStatement s){
		// TODO: short code snippet
		final VarExpr newOutput = (VarExpr) substituteExpr(s.outputVar);
		final Expr newExpr = substituteExpr(s.expr);
		return new AssignmentStatement(newOutput, newExpr);
// throw new ece351.util.Todo351Exception();
	}
	
	
	@Override
	public Expr visitVar(VarExpr e) {
		// TODO replace/substitute the variable found in the map
		// TODO: short code snippet
		if (current_map.containsKey(e.identifier)) {
			return new VarExpr(current_map.get(e.identifier));
		}
		return e;
// throw new ece351.util.Todo351Exception();
	}
	
	// do not rewrite these parts of the AST
	@Override public Expr visitConstant(ConstantExpr e) { return e; }
	@Override public Expr visitNot(NotExpr e) { return e; }
	@Override public Expr visitAnd(AndExpr e) { return e; }
	@Override public Expr visitOr(OrExpr e) { return e; }
	@Override public Expr visitXOr(XOrExpr e) { return e; }
	@Override public Expr visitEqual(EqualExpr e) { return e; }
	@Override public Expr visitNAnd(NAndExpr e) { return e; }
	@Override public Expr visitNOr(NOrExpr e) { return e; }
	@Override public Expr visitXNOr(XNOrExpr e) { return e; }
	@Override public Expr visitNaryAnd(NaryAndExpr e) { return e; }
	@Override public Expr visitNaryOr(NaryOrExpr e) { return e; }
}
