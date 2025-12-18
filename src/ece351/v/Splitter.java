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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import ece351.v.ast.DesignUnit;
import ece351.v.ast.IfElseStatement;
import ece351.v.ast.Process;
import ece351.v.ast.VProgram;

/**
 * Process splitter.
 */
public final class Splitter extends PostOrderExprVisitor {
	private final Set<String> usedVarsInExpr = new LinkedHashSet<String>();

	public static void main(String[] args) {
		System.out.println(split(args));
	}

	public static VProgram split(final String[] args) {
		return split(new CommandLine(args));
	}

	public static VProgram split(final CommandLine c) {
		final VProgram program = DeSugarer.desugar(c);
		return split(program);
	}

	public static VProgram split(final VProgram program) {
		VProgram p = Elaborator.elaborate(program);
		final Splitter s = new Splitter();
		return s.splitit(p);
	}

	private VProgram splitit(final VProgram program) {
		// Determine if the process needs to be split into multiple processes
		// Split the process if there are if/else statements so that the if/else
		// statements only assign values to one pin
		// TODO: longer code snippet
		final List<DesignUnit> resultingDesignUnits = new ArrayList<>();
		for (final DesignUnit du : program.designUnits) {
			final Architecture arch = du.arch;
			final List<Statement> resultingStatements = new ArrayList<>();

			for (final Statement s : arch.statements) {
				if (s instanceof Process) {
					final Process p = (Process) s;
					final List<IfElseStatement> complexIfElses = new ArrayList<>();
					final List<IfElseStatement> simpleIfs = new ArrayList<>();

					for (final Statement ps : p.sequentialStatements) {
						if (ps instanceof IfElseStatement) {
							final IfElseStatement i = (IfElseStatement) ps;
							final Set<String> assignedPins = new HashSet<>();
							for (final Statement ibs : i.ifBody) {
								assignedPins.add(((AssignmentStatement) ibs).outputVar.identifier);
							}
							for (final Statement ebs : i.elseBody) {
								assignedPins.add(((AssignmentStatement) ebs).outputVar.identifier);
							}

							if (assignedPins.size() > 1) {
								// record complex if/else statements
								complexIfElses.add(i);
								// break;
							}
							else {
								// record single if statements
								simpleIfs.add(i);
							}
						}
					}
					// condition1: there is a complex if/else statement that assigns to multiple output pins
					if (!complexIfElses.isEmpty()) {
						
						// process each complex if/else statement
						for (final IfElseStatement complexIfElse : complexIfElses) {
							// use helper method to split complexIfElse into multiple IfElseStatements
							ImmutableList<Statement> splitList = splitIfElseStatement(complexIfElse);

							// create new processes for each small IfElseStatement
							for (Statement stmt : splitList) {
								IfElseStatement smallIf = (IfElseStatement) stmt;

								// recalculate sensitivity list
								usedVarsInExpr.clear();
								traverseExpr(smallIf.condition);
								for (AssignmentStatement as : smallIf.ifBody)
									traverseExpr(as.expr);
								for (AssignmentStatement as : smallIf.elseBody)
									traverseExpr(as.expr);

								ImmutableList<String> sensitivityList = ImmutableList
										.copyOf(new ArrayList<>(usedVarsInExpr));

								// create new process for each small IfElseStatement
								Process newP = new Process(ImmutableList.of(smallIf), sensitivityList);
								resultingStatements.add(newP);
							}
						}
					}
					// condition2: there are multiple single if statements in the process 
					else if (simpleIfs.size() > 1) {
					
						for (final IfElseStatement smallIf : simpleIfs) {
							usedVarsInExpr.clear();
							traverseExpr(smallIf.condition);
							for (AssignmentStatement as : smallIf.ifBody)  traverseExpr(as.expr);
							for (AssignmentStatement as : smallIf.elseBody) traverseExpr(as.expr);

							final ImmutableList<String> sensitivityList =
								ImmutableList.copyOf(new ArrayList<>(usedVarsInExpr));

							final Process newP =
								new Process(ImmutableList.of((Statement) smallIf), sensitivityList);

							resultingStatements.add(newP);
						}
					} else {
						resultingStatements.add(p);
					}
				} else {
					resultingStatements.add(s);
				}
			}

			final Architecture newArch = new Architecture(ImmutableList.copyOf(resultingStatements), arch.components, arch.signals, arch.entityName, arch.architectureName);
			final DesignUnit newDU = new DesignUnit(newArch, du.entity);
			resultingDesignUnits.add(newDU);
		}

		return new VProgram(ImmutableList.copyOf(resultingDesignUnits));
		// throw new ece351.util.Todo351Exception();
	}

	// You do not have to use this helper method, but we found it useful

	private ImmutableList<Statement> splitIfElseStatement(final IfElseStatement ifStmt) {
		// loop over each statement in the ifBody
		// loop over each statement in the elseBody
		// check if outputVars are the same
		// initialize/clear this.usedVarsInExpr
		// call traverse a few times to build up this.usedVarsInExpr
		// build sensitivity list from this.usedVarsInExpr
		// build the resulting list of split statements
		// return result
		// TODO: longer code snippet
		// ArrayList for mutable list building
		final List<Statement> result = new ArrayList<>();
		// use a Map to group assignment statements by their output variable
		final Map<String, AssignmentStatement[]> assignments = new LinkedHashMap<>();

		// 1. go through the if body and group assignments by output variable
		for (final Statement s : ifStmt.ifBody) {
			if (s instanceof AssignmentStatement) {
				final AssignmentStatement a = (AssignmentStatement) s;
				final String outVar = a.outputVar.identifier;
				if (!assignments.containsKey(outVar)) {
					assignments.put(outVar, new AssignmentStatement[2]);
				}
				assignments.get(outVar)[0] = a;
			}
		}

		// 2. go through the else body and group assignments by output variable
		for (final Statement s : ifStmt.elseBody) {
			if (s instanceof AssignmentStatement) {
				final AssignmentStatement a = (AssignmentStatement) s;
				final String outVar = a.outputVar.identifier;
				if (!assignments.containsKey(outVar)) {
					assignments.put(outVar, new AssignmentStatement[2]);
				}
				assignments.get(outVar)[1] = a;
			}
		}

		// 3. For each output variable, create a new, simple IfElseStatement
		for (final Map.Entry<String, AssignmentStatement[]> entry : assignments.entrySet()) {
			final AssignmentStatement ifAssign = entry.getValue()[0];
			final AssignmentStatement elseAssign = entry.getValue()[1];

			// We assume that if a var is assigned in one branch, it's assigned in the
			// other.
			if (ifAssign != null && elseAssign != null) {
				final ImmutableList<AssignmentStatement> newIfBody = ImmutableList.of(ifAssign);
				final ImmutableList<AssignmentStatement> newElseBody = ImmutableList.of(elseAssign);
				final IfElseStatement newIfStmt = new IfElseStatement(newElseBody, newIfBody, ifStmt.condition);
				result.add(newIfStmt);
			}
		}

		// Convert ArrayList to ImmutableList here
		return ImmutableList.copyOf(result);
		// throw new ece351.util.Todo351Exception();
	}

	@Override
	public Expr visitVar(final VarExpr e) {
		this.usedVarsInExpr.add(e.identifier);
		return e;
	}

	// no-ops
	@Override
	public Expr visitConstant(ConstantExpr e) {
		return e;
	}

	@Override
	public Expr visitNot(NotExpr e) {
		return e;
	}

	@Override
	public Expr visitAnd(AndExpr e) {
		return e;
	}

	@Override
	public Expr visitOr(OrExpr e) {
		return e;
	}

	@Override
	public Expr visitXOr(XOrExpr e) {
		return e;
	}

	@Override
	public Expr visitNAnd(NAndExpr e) {
		return e;
	}

	@Override
	public Expr visitNOr(NOrExpr e) {
		return e;
	}

	@Override
	public Expr visitXNOr(XNOrExpr e) {
		return e;
	}

	@Override
	public Expr visitEqual(EqualExpr e) {
		return e;
	}

	@Override
	public Expr visitNaryAnd(NaryAndExpr e) {
		return e;
	}

	@Override
	public Expr visitNaryOr(NaryOrExpr e) {
		return e;
	}

}
