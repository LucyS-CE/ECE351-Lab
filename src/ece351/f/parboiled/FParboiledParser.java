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

package ece351.f.parboiled;

import java.lang.invoke.MethodHandles;

import org.parboiled.Rule;

import ece351.common.ast.AndExpr;
import ece351.common.ast.AssignmentStatement;
import ece351.common.ast.ConstantExpr;
import ece351.common.ast.Constants;
import ece351.common.ast.Expr;
import ece351.common.ast.NotExpr;
import ece351.common.ast.OrExpr;
import ece351.common.ast.VarExpr;
import ece351.f.ast.FProgram;
import ece351.util.CommandLine;

// Parboiled requires that this class not be final
public /* final */ class FParboiledParser extends FBase implements Constants {

	public static Class<?> findLoadedClass(String className) throws IllegalAccessException {
		try {
			return MethodHandles.lookup().findClass(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public static Class<?> loadClass(byte[] code) throws IllegalAccessException {
		return MethodHandles.lookup().defineClass(code);
	}

	public static void main(final String[] args) {
		final CommandLine c = new CommandLine(args);
		final String input = c.readInputSpec();
		final FProgram fprogram = parse(input);
		assert fprogram.repOk();
		final String output = fprogram.toString();

		// if we strip spaces and parens input and output should be the same
		if (strip(input).equals(strip(output))) {
			// success: return quietly
			return;
		} else {
			// failure: make a noise
			System.err.println("parsed value not equal to input:");
			System.err.println("    " + strip(input));
			System.err.println("    " + strip(output));
			System.exit(1);
		}
	}

	private static String strip(final String s) {
		return s.replaceAll("\\s", "").replaceAll("\\(", "").replaceAll("\\)", "");
	}

	public static FProgram parse(final String inputText) {
		final FProgram result = (FProgram) process(FParboiledParser.class, inputText).resultValue;
		assert result.repOk();
		return result;
	}

	@Override
	public Rule Program() {
		// TODO: longer code snippet
		return Sequence(push(new FProgram()), OneOrMore(Formula()), EOI); // 1. [->FProgram]
// throw new ece351.util.Todo351Exception();
	}
	public Rule Formula() {
		return Sequence(
				W0(), Var(),  // 2. do something in Var()
				W0(), "<=", W0(), Expr(), W0(), ';', W0(), // 3. do something in Expr()
				swap(), 													 // 4. [VarExpr(Var), Expr, FProgram]
				push(new AssignmentStatement((VarExpr)pop(), (Expr)pop())),  // 5. [->AssignmentStatement(VarExpr, Expr), FProgram]
				swap(),	 													 // 6. [FProgram, AssignmentStatement(Var)]
				push(((FProgram) pop()).append((AssignmentStatement) pop()))  // 7. FProgram<-[AssignmentStatement(VarExpr, Expr)]
																			  // 8. AssignmentStatement(VarExpr, Expr)<-[]
																			  // 9. [->FProgram.append(AssignmentStatement(VarExpr, Expr))]
		);
	}

	public Rule Expr() {
		return Sequence(
			W0(), Term(), W0(),		// 3.1. do something in Term()
			ZeroOrMore(
				Sequence(
					W0(), "or", W0(), Term(),  // 3.1. again, do something in Term()
					swap(),  // 3.2. [VarExpr(Term1), VarExpr(Term2), AssignmentStatement, FProgram]
					push(new OrExpr((Expr) pop(), (Expr) pop()))  // 3.3. VarExpr(Term1)<-[VarExpr(Term2), AssignmentStatement, FProgram]
																// 3.4. VarExpr(Term2)<-[AssignmentStatement, FProgram]
																// 3.5. [->OrExpr(VarExpr(Term1), VarExpr(Term2)), AssignmentStatement, FProgram]
				)
			)
		);
	}

	public Rule Term() {
		return Sequence(
			W0(), Factor(), W0(),  // 3.1.1. do something in Factor()
			ZeroOrMore(
				Sequence(
					W0(), "and", W0(), Factor(),  // 3.1.1. do something in Factor()
					swap(),  // 3.1.2. [VarExpr(Factor1), VarExpr(Factor2), FProgram]
					push(new AndExpr((Expr) pop(), (Expr) pop()))  // 3.1.3. VarExpr(Factor1)<-[VarExpr(Factor2), FProgram]
																// 3.1.4. VarExpr(Factor2)<-[FProgram]
																// 3.1.5. [->AndExpr(VarExpr(Factor1), VarExpr(Factor2)), FProgram]
				)
			)
		);
	}

	public Rule Factor() {
		return Sequence(
			W0(),
			FirstOf(
				Sequence(
					"not", W0(), Factor(),
					push(new NotExpr((Expr) pop()))  // 3.1.1.1. [->NotExpr(Expr), FProgram]
				),
				Sequence(
					'(', W0(), Expr(), W0(), ')'
				),
				Var(), Constant()
			)
		);
	}

	public Rule Var() {
		return Sequence(
			W0(), OneOrMore(Id()),
			push(new VarExpr((String)(match())))	 // [->VarExpr, FProgram]
		);
	}

	public Rule Id() {
		return FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9'), '_');
	}

	public Rule Constant() {
		return FirstOf(
			Sequence("'0'", push(ConstantExpr.FalseExpr)), 
			Sequence("'1'", push(ConstantExpr.TrueExpr))
		);
	}
}
