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
import java.util.List;

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
import ece351.f.ast.FProgram;
import ece351.util.CommandLine;
import ece351.v.ast.DesignUnit;
import ece351.v.ast.IfElseStatement;
import ece351.v.ast.Process;
import ece351.v.ast.VProgram;

/**
 * Translates VHDL to F.
 */
public final class Synthesizer extends PostOrderExprVisitor {
	
	private String varPrefix;
	private int condCount;
	private static String conditionPrefix = "condition";
	
	public static void main(String[] args) { 
		System.out.println(synthesize(args));
	}
	
	public static FProgram synthesize(final String[] args) {
		return synthesize(new CommandLine(args));
	}
	
	public static FProgram synthesize(final CommandLine c) {
        final VProgram program = DeSugarer.desugar(c);
        return synthesize(program);
	}
	
	public static FProgram synthesize(final VProgram program) {
		VProgram p = Splitter.split(program);
		final Synthesizer synth = new Synthesizer();
		return synth.synthesizeit(p);
	}
	
	public Synthesizer(){
		condCount = 0;
	}
		
	private FProgram synthesizeit(final VProgram root) {	
		FProgram result = new FProgram();
		// set varPrefix for this design unit
		// TODO: longer code snippet
		final List<AssignmentStatement> assignments = new ArrayList<>();

		for (final DesignUnit du : root.designUnits) {
			// 1. set varPrefix for each design unit
 			this.varPrefix = du.entity.identifier;

			for (final Statement s : du.arch.statements) {
				if (s instanceof AssignmentStatement) {
					final AssignmentStatement as = (AssignmentStatement) s;

					// 2. traverse both left and right sides to apply visitVar
					final VarExpr newOut = (VarExpr) traverseExpr(as.outputVar);
					final Expr newExpr = traverseExpr(as.expr);

					assignments.add(new AssignmentStatement(newOut, newExpr));

				} else if (s instanceof Process) {
					final Process p = (Process) s;

					for (final Statement ps : p.sequentialStatements) {
						if (ps instanceof AssignmentStatement) {
							final AssignmentStatement as = (AssignmentStatement) ps;

							final VarExpr newOut = (VarExpr) traverseExpr(as.outputVar);
							final Expr newExpr = traverseExpr(as.expr);

							assignments.add(new AssignmentStatement(newOut, newExpr));

						} else if (ps instanceof IfElseStatement) {
							final FProgram fp = implication((IfElseStatement) ps);
							assignments.addAll(fp.formulas);
						}
					}
				}
			}
		}

		return new FProgram(ImmutableList.copyOf(assignments));
// throw new ece351.util.Todo351Exception();
		// return result;
	}
	
	private FProgram implication(final IfElseStatement statement) {
		// error checking
		if( statement.ifBody.size() != 1) {
			throw new IllegalArgumentException("if/else statement: " + statement + "\n can only have one assignment statement in the if-body and else-body where the output variable is the same!");
		}
		if (statement.elseBody.size() != 1) {
			throw new IllegalArgumentException("if/else statement: " + statement + "\n can only have one assignment statement in the if-body and else-body where the output variable is the same!");
		}
		final AssignmentStatement ifb = statement.ifBody.get(0);
		final AssignmentStatement elb = statement.elseBody.get(0);
		if (!ifb.outputVar.equals(elb.outputVar)) {
			throw new IllegalArgumentException("if/else statement: " + statement + "\n can only have one assignment statement in the if-body and else-body where the output variable is the same!");
		}

		// build result
		// TODO: longer code snippet
		// Re-write the if-else statement as a single boolean expression.
		// This is the general form:
		// ( cond AND if_expr ) OR ( NOT cond AND else_expr )

		// rename the variables in cond, if_expr, else_expr with varPrefix
		final Expr cond = traverseExpr(statement.condition);
		final Expr ifExpr = traverseExpr(ifb.expr);
		final Expr elseExpr = traverseExpr(elb.expr);
		final VarExpr out = (VarExpr) traverseExpr(ifb.outputVar);

		condCount++;
		final VarExpr condVar = new VarExpr(conditionPrefix + condCount);

		// 1. conditionN <= cond;
		final AssignmentStatement condAssign = new AssignmentStatement(condVar, cond);

		// 2. out <= (conditionN and ifExpr) or (not conditionN and elseExpr);
		// (cond AND if_expr)
		final Expr term1 = new AndExpr(condVar, ifExpr);
		// (NOT cond AND else_expr)
		final Expr term2 = new AndExpr(new NotExpr(condVar), elseExpr);
		// (term1 OR term2)
		final Expr finalExpr = new OrExpr(term1, term2);

		// create the new assignment statement, using the original output variable
		final AssignmentStatement mainAssign = new AssignmentStatement(out, finalExpr);

		// return a new FProgram containing this single assignment
		return new FProgram(ImmutableList.of(condAssign, mainAssign));
// throw new ece351.util.Todo351Exception();
	}

	/** Rewrite var names with prefix to mitigate name collision. */
	@Override
	public Expr visitVar(final VarExpr e) {
		return new VarExpr(varPrefix + e.identifier);
	}
	
	@Override public Expr visitConstant(ConstantExpr e) { return e; }
	@Override public Expr visitNot(NotExpr e) { return e; }
	@Override public Expr visitAnd(AndExpr e) { return e; }
	@Override public Expr visitOr(OrExpr e) { return e; }
	@Override public Expr visitNaryAnd(NaryAndExpr e) { return e; }
	@Override public Expr visitNaryOr(NaryOrExpr e) { return e; }
	
	// We shouldn't see these in the AST, since F doesn't support them
	// They should have been desugared away previously
	@Override public Expr visitXOr(XOrExpr e) { throw new IllegalStateException("xor not desugared"); } 
	@Override public Expr visitEqual(EqualExpr e) { throw new IllegalStateException("EqualExpr not desugared"); }
	@Override public Expr visitNAnd(NAndExpr e) { throw new IllegalStateException("nand not desugared"); }
	@Override public Expr visitNOr(NOrExpr e) { throw new IllegalStateException("nor not desugared"); }
	@Override public Expr visitXNOr(XNOrExpr e) { throw new IllegalStateException("xnor not desugared"); }
	
}



