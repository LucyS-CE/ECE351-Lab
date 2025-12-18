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

package ece351.common.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.parboiled.common.ImmutableList;

import ece351.util.Examinable;
import ece351.util.Examiner;

/**
 * An expression with multiple children. Must be commutative.
 */
public abstract class NaryExpr extends Expr {

	public final ImmutableList<Expr> children;

	public NaryExpr(final Expr... exprs) {
		Arrays.sort(exprs);
		ImmutableList<Expr> c = ImmutableList.of();
		for (final Expr e : exprs) {
			c = c.append(e);
		}
    	this.children = c;
	}
	
	public NaryExpr(final List<Expr> children) {
		final ArrayList<Expr> a = new ArrayList<Expr>(children);
		Collections.sort(a);
		this.children = ImmutableList.copyOf(a);
	}

	/**
	 * Each subclass must implement this factory method to return
	 * a new object of its own type. 
	 */
	public abstract NaryExpr newNaryExpr(final List<Expr> children);

	/**
	 * Construct a new NaryExpr (of the appropriate subtype) with 
	 * one extra child.
	 * @param e the child to append
	 * @return a new NaryExpr
	 */
	public NaryExpr append(final Expr e) {
		return newNaryExpr(children.append(e));
	}

	/**
	 * Construct a new NaryExpr (of the appropriate subtype) with 
	 * the extra children.
	 * @param list the children to append
	 * @return a new NaryExpr
	 */
	public NaryExpr appendAll(final List<Expr> list) {
		final List<Expr> a = new ArrayList<Expr>(children.size() + list.size());
		a.addAll(children);
		a.addAll(list);
		return newNaryExpr(a);
	}

	/**
	 * Check the representation invariants.
	 */
	public boolean repOk() {
		// programming sanity
		assert this.children != null;
		// should not have a single child: indicates a bug in simplification
		assert this.children.size() > 1 : "should have more than one child, probably a bug in simplification";
		// check that children is sorted
		int i = 0;
		for (int j = 1; j < this.children.size(); i++, j++) {
			final Expr x = this.children.get(i);
			assert x != null : "null children not allowed in NaryExpr";
			final Expr y = this.children.get(j);
			assert y != null : "null children not allowed in NaryExpr";
			assert x.compareTo(y) <= 0 : "NaryExpr.children must be sorted";
		}
        // Note: children might contain duplicates --- not checking for that
        // ... maybe should check for duplicate children ...
		// no problems found
		return true;
	}

	/**
	 * The name of the operator represented by the subclass.
	 * To be implemented by each subclass.
	 */
	public abstract String operator();
	
	/**
	 * The complementary operation: NaryAnd returns NaryOr, and vice versa.
	 */
	abstract protected Class<? extends NaryExpr> getThatClass();
	

	/**
     * e op x = e for absorbing element e and operator op.
     * @return
     */
	public abstract ConstantExpr getAbsorbingElement();

    /**
     * e op x = x for identity element e and operator op.
     * @return
     */
	public abstract ConstantExpr getIdentityElement();


	@Override 
    public final String toString() {
    	final StringBuilder b = new StringBuilder();
    	b.append("(");
    	int count = 0;
    	for (final Expr c : children) {
    		b.append(c);
    		if (++count  < children.size()) {
    			b.append(" ");
    			b.append(operator());
    			b.append(" ");
    		}
    		
    	}
    	b.append(")");
    	return b.toString();
    }


	@Override
	public final int hashCode() {
		return 17 + children.hashCode();
	}

	@Override
	public final boolean equals(final Object obj) {
		if (!(obj instanceof Examinable)) return false;
		return examine(Examiner.Equals, (Examinable)obj);
	}
	
	@Override
	public final boolean isomorphic(final Examinable obj) {
		return examine(Examiner.Isomorphic, obj);
	}
	
	private boolean examine(final Examiner e, final Examinable obj) {
		// basics
		if (obj == null) return false;
		if (!this.getClass().equals(obj.getClass())) return false;
		final NaryExpr that = (NaryExpr) obj;
		
		// TODO: longer code snippet

		// if the number of children are different, consider them not equivalent
		if (this.children.size() != that.children.size())
			return false;
		// since the n-ary expressions have the same number of children and they are
		// sorted, just iterate and check
		// supposed to be sorted, but might not be (because repOk might not pass)
		// if they are not the same elements in the same order return false
		for (int i = 0; i < this.children.size(); i++) {
			final Examinable this_child = this.children.get(i);
			final Examinable that_child = that.children.get(i);
			if (!e.examine(this_child, that_child))
				return false;
		}
		// no significant differences found, return true
		return true;
// throw new ece351.util.Todo351Exception();
	}

	
	@Override
	protected final Expr simplifyOnce() {
		assert repOk();
		final Expr result = 
				simplifyChildren().
				mergeGrandchildren().
				foldIdentityElements().
				foldAbsorbingElements().
				foldComplements().
				removeDuplicates().
				simpleAbsorption().
				subsetAbsorption().
				singletonify();
		assert result.repOk();
		return result;
	}
	
	/**
	 * Call simplify() on each of the children.
	 */
	private NaryExpr simplifyChildren() {
		// note: we do not assert repOk() here because the rep might not be ok
		// the result might contain duplicate children, and the children
		// might be out of order
		// return this; // TODO: replace this stub

		final List<Expr> simplifiedChildren = new ArrayList<Expr>(children.size());
		boolean changed = false;
		for (final Expr e : children) {
			final Expr simplified = e.simplify();
			if (simplified != e) {
				changed = true;
			}
			simplifiedChildren.add(simplified);
		}

		if (changed) {
			return newNaryExpr(simplifiedChildren);
		} else {
			return this;
		}
	}

	/**
	 * Merge grandchildren of the same type as this
	 */
	private NaryExpr mergeGrandchildren() {
		// TODO: replace this stub
		
		// extract children to merge using filter (because they are the same type as us)
		final List<Expr> toMerge = new ArrayList<>();
		final List<Expr> others = new ArrayList<>();
		for (final Expr c : children) {
			if (c.getClass().equals(this.getClass())) {
				toMerge.add(c);
			} else {
				others.add(c);
			}
		}

		// if no children to merge, then return this (i.e., no change)
		if (toMerge.isEmpty()) {
			return this;
		}
		
		// merge in the grandchildren
		final List<Expr> newChildren = new ArrayList<>(others);
		for (final Expr c : toMerge) {
			newChildren.addAll(((NaryExpr)c).children);
		}
		
		// create the result
		final NaryExpr result = newNaryExpr(newChildren);
		
		// assert result.repOk():  this operation should always leave the AST in a legal state
		assert result.repOk();
		return result;
	}

	/**
	 * Remove identity elements from the list of children
	 */
	private NaryExpr foldIdentityElements() {
		// TODO: replace this stub

		// if we have only one child stop now and return self
		if (children.size() == 1) {
			return this;
		}

		// we have multiple children, remove the identity elements, identity element is true for AND and false for OR
		final ConstantExpr identity = this.getIdentityElement();

		// add non-identity elements to working list
		final List<Expr> working = new ArrayList<Expr>();
		for (final Expr e : children) {
			if (!e.equals(identity)) {
				working.add(e);
			}
		}

		// deal with the list
		if (working.size() == children.size()) {
			// no change
			return this;
		} else if (working.isEmpty()) {
			// all children were identity elements, so now our working list is empty
			// return a new list with a single identity element
			List<Expr> single = new ArrayList<Expr>();
			single.add(identity);
			return newNaryExpr(single);
		} else {
			// normal return
			return newNaryExpr(working);
		}
		// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
	}

	/**
	 * Remove absorbing elements from the list of children
	 */
	private NaryExpr foldAbsorbingElements() {
		// absorbing element: 0.x=0 and 1+x=1
		// absorbing element is present: return it
		// not so fast! what is the return type of this method? why does it have to be that way?
		// no absorbing element present, do nothing
		// return this; // TODO: replace this stub
		// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)

		final ConstantExpr absorbing = this.getAbsorbingElement();

		for (final Expr e : children) {
			if (e.equals(absorbing)) {
				// absorbing element is present: return it
				List<Expr> single = new ArrayList<Expr>();
				single.add(absorbing);
				return newNaryExpr(single);
			}
		}

		// no absorbing element present, do nothing
		return this;
	}
	
	/**
	 * Collapse complements
	 */
	private NaryExpr foldComplements() {
		// collapse complements
		// !x . x . ... = 0 and !x + x + ... = 1
		// x op !x = absorbing element
		// find all negations
		// for each negation, see if we find its complement
				// found matching negation and its complement
				// return absorbing element
		// no complements to fold
		// return this; // TODO: replace this stub
		// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
		
		// use two list to separate regulars and negations
		final List<Expr> regulars = new ArrayList<Expr>();
		final List<NotExpr> negations = new ArrayList<NotExpr>();
		for (final Expr e : children) {
			if (e instanceof NotExpr) {
				negations.add((NotExpr) e);
			} else {
				regulars.add(e);
			}
		}

		// for each negation, see if we find its complement
			// found matching negation and its complement
			// return absorbing element
		for (final NotExpr n : negations) {
			if (regulars.contains(n.expr)) {
				// found a complement, so return the absorbing element
				final List<Expr> result = new ArrayList<Expr>();
				result.add(this.getAbsorbingElement());
				return newNaryExpr(result);
			}
		}

		// no complements to fold
		return this;
	}

	private NaryExpr removeDuplicates() {
		// remove duplicate children: x.x=x and x+x=x
		// since children are sorted this is fairly easy
			// no changes
			// removed some duplicates
		// return this; // TODO: replace this stub
		// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
		
		// remove duplicate children: x.x=x and x+x=x
		final List<Expr> working = new ArrayList<Expr>();
		Expr last = null;
		for (final Expr child : children) {
			if (last == null || !child.equals(last)) {
				working.add(child);
				last = child;
			}
		}

		// no changes
		if (working.size() == children.size()) {
			return this;
		}

		// removed some duplicates
		return newNaryExpr(working);
		// do not assert repOk(): this fold might leave the AST in an illegal state (with only one child)
	}

	private NaryExpr simpleAbsorption() {
		// (x.y) + x ... = x ...
		// check if there are any conjunctions that can be removed
		// return this; // TODO: replace this stub
		// do not assert repOk(): this operation might leave the AST in an illegal state (with only one child)
		
		final List<Expr> absorbed = new ArrayList<>(); 	// list for final absorbed expression(to be removed)
		final List<Expr> absorbers = new ArrayList<>();  // list for simple absorbers
		final Class<? extends NaryExpr> oppositeType = operator().equals(Constants.AND) ? NaryOrExpr.class : NaryAndExpr.class;	// for AND, absorbees are ORs, and for OR, absorbees are ANDs

		// separate children into absorbers and potential absorbees
		final List<NaryExpr> absorbees = new ArrayList<>();  // list for potential absorbees
		for (final Expr child : children) {
			if (oppositeType.isInstance(child)) {
				absorbees.add((NaryExpr) child);
			} else {
				absorbers.add(child);
			}
		}

		// check for absorption
		for (final NaryExpr absorbee : absorbees) {
			for (final Expr subChild : absorbee.children) {
				// case1: simple absorption, e.g., a + a*b
				if (absorbers.contains(subChild)) {
					absorbed.add(absorbee);
					break;
				}

				// case2?subset absorption, e.g., (a+b) + c*(a+b)
				if (subChild.getClass().equals(this.getClass())) {
					if (absorbers.containsAll(((NaryExpr) subChild).children)) {
						absorbed.add(absorbee);
						break;
					}
				}
			}
		}

		// simplify
		if (absorbed.isEmpty()) {
			return this;
		} else {
			final List<Expr> working = new ArrayList<>(children);
			working.removeAll(absorbed);
			return newNaryExpr(working);
		}
	}

	private NaryExpr subsetAbsorption() {
		// check if there are any conjunctions that are supersets of others
		// e.g., ( a . b . c ) + ( a . b ) = a . b
		// return this; // TODO: replace this stub
		// do not assert repOk(): this operation might leave the AST in an illegal state (with only one child)
		
		// determine the type of clauses to look for (e.g., AndExpr inside OrExpr)
		final Class<? extends NaryExpr> clauseType = operator().equals(Constants.AND) ? NaryOrExpr.class : NaryAndExpr.class;

		// filter to get only the clauses of the target type
		final List<NaryExpr> clauses = new ArrayList<>();
		for (final Expr child : children) {
			if (clauseType.isInstance(child)) {
				clauses.add((NaryExpr) child);
			}
		}

		// not enough clauses to compare
		if (clauses.size() < 2) {
			return this;
		}

		// find all supersets that can be removed
		final List<Expr> toRemove = new ArrayList<>();
		for (int i = 0; i < clauses.size(); i++) {
			for (int j = i + 1; j < clauses.size(); j++) {
				final NaryExpr c1 = clauses.get(i);
				final NaryExpr c2 = clauses.get(j);

				// if c1's children are a superset of c2's, then c1 can be removed
				if (c1.children.containsAll(c2.children)) {
					toRemove.add(c1);
				}
				// if c2's children are a superset of c1's, then c2 can be removed
				else if (c2.children.containsAll(c1.children)) {
					toRemove.add(c2);
				}
			}
		}

		// Remove the supersets and return a new expression
		if (toRemove.isEmpty()) {
			return this;
		} else {
			final List<Expr> working = new ArrayList<>(this.children);
			working.removeAll(toRemove);
			return newNaryExpr(working);
		}
	}

	/**
	 * If there is only one child, return it (the containing NaryExpr is unnecessary).
	 */
	private Expr singletonify() {
		// if we have only one child, return it
		// having only one child is an illegal state for an NaryExpr
			// multiple children; nothing to do; return self
			// return this; // TODO: replace this stub
		
		if (children.size() == 1) {
			return children.get(0); // imagine container is UnaryExpr, while it only contains a single VarExpr, we don't need the container anymore, so we take the item out of the container
		} else {
			return this;
		}
	}

	/**
	 * Return a new NaryExpr with only the children of a certain type, 
	 * or excluding children of a certain type. 
	 * Can only filter by class type, not same class with diff value
	 * @param filter
	 * @param shouldMatchFilter
	 * @return
	 */
	public final NaryExpr filter(final Class<? extends Expr> filter, final boolean shouldMatchFilter) {
		ImmutableList<Expr> l = ImmutableList.of();
		for (final Expr child : children) {
			if (child.getClass().equals(filter)) {
				if (shouldMatchFilter) {
					l = l.append(child);
				}
			} else {
				if (!shouldMatchFilter) {
					l = l.append(child);
				}
			}
		}
		return newNaryExpr(l);
	}

	public final NaryExpr filter(final Expr filter, final Examiner examiner, final boolean shouldMatchFilter) {
		ImmutableList<Expr> l = ImmutableList.of();
		for (final Expr child : children) {
			if (examiner.examine(child, filter)) {
				if (shouldMatchFilter) {
					l = l.append(child);
				}
			} else {
				if (!shouldMatchFilter) {
					l = l.append(child);
				}
			}
		}
		return newNaryExpr(l);
	}

	public final NaryExpr removeAll(final List<Expr> toRemove, final Examiner examiner) {
		NaryExpr result = this;
		for (final Expr e : toRemove) {
			result = result.filter(e, examiner, false);
		}
		return result;
	}

	public final boolean contains(final Expr expr, final Examiner examiner) {
		for (final Expr child : children) {
			if (examiner.examine(child, expr)) {
				return true;
			}
		}
		return false;
	}

}
