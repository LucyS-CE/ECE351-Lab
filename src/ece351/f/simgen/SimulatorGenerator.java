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

package ece351.f.simgen;

import java.io.PrintWriter;

import ece351.common.ast.AndExpr;
import ece351.common.ast.AssignmentStatement;
import ece351.common.ast.BinaryExpr;
import ece351.common.ast.ConstantExpr;
import ece351.common.ast.EqualExpr;
import ece351.common.ast.Expr;
import ece351.common.ast.NAndExpr;
import ece351.common.ast.NOrExpr;
import ece351.common.ast.NaryAndExpr;
import ece351.common.ast.NaryExpr;
import ece351.common.ast.NaryOrExpr;
import ece351.common.ast.NotExpr;
import ece351.common.ast.OrExpr;
import ece351.common.ast.UnaryExpr;
import ece351.common.ast.VarExpr;
import ece351.common.ast.XNOrExpr;
import ece351.common.ast.XOrExpr;
import ece351.common.visitor.ExprVisitor;
import ece351.f.FParser;
import ece351.f.analysis.DetermineInputVars;
import ece351.f.ast.FProgram;
import ece351.util.CommandLine;

// added import
import java.io.File;
import java.util.*;
import java.util.stream.*;


public final class SimulatorGenerator extends ExprVisitor {

	private PrintWriter out = new PrintWriter(System.out);
	private String indent = "";

	public static void main(final String arg) {
		main(new String[]{arg});
	}
	
	public static void main(final String[] args) {
		final CommandLine c = new CommandLine(args);
		final SimulatorGenerator s = new SimulatorGenerator();
		final PrintWriter pw = new PrintWriter(System.out);
		s.generate(c.getInputName(), FParser.parse(c), pw);
		pw.flush();
	}

	private void println(final String s) {
		out.print(indent);
		out.println(s);
	}
	
	private void println() {
		out.println();
	}
	
	private void print(final String s) {
		out.print(s);
	}

	private void indent() {
		indent = indent + "    ";
	}
	
	private void outdent() {
		indent = indent.substring(0, indent.length() - 4);
	}
	
	public void generate(final String fName, final FProgram program, final PrintWriter out) {
		this.out = out;
		final String cleanFName = fName.replace('-', '_');
		
		// header
		println("import java.util.*;");
		println("import ece351.w.ast.*;");
		println("import ece351.w.parboiled.*;");
		println("import static ece351.util.Boolean351.*;");
		println("import ece351.util.CommandLine;");
		println("import java.io.File;");
		println("import java.io.FileWriter;");
		println("import java.io.StringWriter;");
		println("import java.io.PrintWriter;");
		println("import java.io.IOException;");
		println("import ece351.util.Debug;");
		println();
		println("public final class Simulator_" + cleanFName + " {");
		indent();
		println("public static void main(final String[] args) {");
		indent();
		
		println("// read the input F program");
		println("// write the output");
		println("// read input WProgram");
		println("// construct storage for output");
		println("// loop over each time step");
		println("// values of input variables at this time step");
		println("// values of output variables at this time step");
		println("// store outputs");
		// end the time step loop
		// boilerplate
		println("// write the input");
		println("// write the output");

		// TODO: longer code snippet
		println("final CommandLine cmd = new CommandLine(args);");
		println("final String input = cmd.readInputSpec();");
		println("final WProgram wprogram = WParboiledParser.parse(input);");

		println("final Map<String,StringBuilder> output = new LinkedHashMap<String,StringBuilder>();");
		for (final AssignmentStatement f : program.formulas) {
			println("output.put(\"" + f.outputVar.identifier + "\", new StringBuilder());");
		}

		println("final int timeCount = wprogram.timeCount();");
		println("for (int time = 0; time < timeCount; time++) {");
		indent();

		final SortedSet<String> allInputVars = DetermineInputVars.inputVars(program);
		println("// values of input variables at this time step");
		for (final String inputVar : allInputVars) {
			println("final boolean in_" + inputVar + " = wprogram.valueAtTime(\"" + inputVar + "\", time);");
		}

		println("// values of output variables at this time step");
		for (final AssignmentStatement f : program.formulas) {
			final Set<String> inputs = DetermineInputVars.inputVars(f);
			String argsList = "";
			if (!inputs.isEmpty()) {
				// build the argument list for the call, e.g., "in_a, in_b"
				final Stream<String> s = inputs.stream();
				argsList = s.sorted().map(v -> "in_" + v).collect(Collectors.joining(", "));
			}
			// call the function, e.g., "gen_x(in_a, in_b)"
			// println("final boolean out_" + f.outputVar.identifier + " = " + generateCall(f).replace(f.outputVar.toString(), f.outputVar.toString() + "(" + argsList) + ";");
			final String call = generateCall(f);
			final String funcName = call.substring(0, call.indexOf('('));
			println("final boolean out_" + f.outputVar.identifier + " = " + funcName + "(" + argsList + ");");
		}

		println("// store outputs");
		for (final AssignmentStatement f : program.formulas) {
			println("output.get(\"" + f.outputVar.identifier + "\").append(out_" + f.outputVar.identifier + " ? '1' : '0');");
		}

		outdent();
		println("}");

		// write output file in try
		println("try {");
		indent();
		println("final File f = cmd.getOutputFile();");
		println("f.getParentFile().mkdirs();");
		println("final PrintWriter pw = new PrintWriter(new FileWriter(f));");
		println("pw.println(wprogram.toString());");
		println("for (final java.util.Map.Entry<String,StringBuilder> e : output.entrySet()) {");
		indent();
		println("pw.write(e.getKey() + \" : \" + e.getValue().toString() + \";\\n\");");
		outdent();
		println("}");
		println("pw.close();");
		outdent();
		println("} catch (final IOException e) {");
		indent();
		println("Debug.barf(e.getMessage());");
		outdent();
		println("}"); 
// throw new ece351.util.Todo351Exception();
		// end main method
		outdent();
		println("}");
		
		println("// methods to compute values for output pins");
		// TODO: longer code snippet
		for (final AssignmentStatement f : program.formulas) {
			println(generateSignature(f) + " {");
			indent();
			print(indent + "return ");
			traverseExpr(f.expr);
			println(";");
			outdent();
			println("}");
		}
// throw new ece351.util.Todo351Exception();
		// end class
		outdent();
		println("}");

	}

	@Override
	public Expr traverseNaryExpr(final NaryExpr e) {
		e.accept(this);
		final int size = e.children.size();
		for (int i = 0; i < size; i++) {
			final Expr c = e.children.get(i);
			traverseExpr(c);
			if (i < size - 1) {
				// common case
				out.print(", ");
			}
		}
		out.print(")");
		return e;
	}

	@Override
	public Expr traverseBinaryExpr(final BinaryExpr e) {
		e.accept(this);
		traverseExpr(e.left);
		out.print(", ");
		traverseExpr(e.right);
		out.print(")");
		return e;
	}

	@Override
	public Expr traverseUnaryExpr(final UnaryExpr e) {
		e.accept(this);
		traverseExpr(e.expr);
		out.print(")");
		return e;
	}

	@Override
	public Expr visitConstant(final ConstantExpr e) {
		out.print(Boolean.toString(e.b));
		return e;
	}

	@Override
	public Expr visitVar(final VarExpr e) {
		out.print(e.identifier);
		return e;
	}

	@Override public Expr visitNot(final NotExpr e) { return visitOp(e); }
	@Override public Expr visitAnd(final AndExpr e) { return visitOp(e); }
	@Override public Expr visitOr(final OrExpr e) { return visitOp(e); }
	@Override public Expr visitNaryAnd(final NaryAndExpr e) { return visitOp(e); }
	@Override public Expr visitNaryOr(final NaryOrExpr e) { return visitOp(e); }
	@Override public Expr visitNOr(final NOrExpr e) { return visitOp(e); }
	@Override public Expr visitXOr(final XOrExpr e) { return visitOp(e); }
	@Override public Expr visitXNOr(final XNOrExpr e) { return visitOp(e); }
	@Override public Expr visitNAnd(final NAndExpr e) { return visitOp(e); }
	@Override public Expr visitEqual(final EqualExpr e) { return visitOp(e); }
	
	private Expr visitOp(final Expr e) {
		out.print(e.operator());
		out.print("(");
		return e;
	}
	
	public String generateSignature(final AssignmentStatement f) {
		return generateList(f, true);
	}
	
	public String generateCall(final AssignmentStatement f) {
		return generateList(f, false);
	}

	// signature == true: generate the method signature; ex. final boolean a, final boolean b
	// signature == false: generate the method call; ex. a, b
	private String generateList(final AssignmentStatement f, final boolean signature) {
		final StringBuilder b = new StringBuilder();
		if (signature) {
			b.append("public static boolean ");
		}
		b.append("gen_");  // adding prefix for generated java function
		b.append(f.outputVar);
		b.append("(");
		// loop over f's input variables
		// TODO: longer code snippet
		final List<String> inputVars = new ArrayList<>(DetermineInputVars.inputVars(f));
		Collections.sort(inputVars);	 // use sorted list to ensure order matches between signature and call
		if (!inputVars.isEmpty()) {
			String prefix = "final boolean ";
			if (signature) {
				for (String s : inputVars) {
					b.append(prefix + s + ", ");	// add prefix
				}
			} else {
				for (String s : inputVars) {
					b.append(s + ", ");
				}
			}
			b.setLength(b.length() - 2); // remove ", "(2 tokens) at the end of string
		}
// throw new ece351.util.Todo351Exception();
		b.append(")");
		return b.toString();
	}

}
