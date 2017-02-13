package Parser

sealed trait AST
sealed trait Statement extends AST

/* All expressions are statements. We relegate the pruning of expressions
 * that don't have effects to a later analysis */
sealed trait Expression extends Statement
sealed trait Declaration extends AST

/* Expressions */
case class Variable(x : String) extends Expression
case class NumLiteral(value : Int) extends Expression
case class Conjunction(e1 : Expression, e2 : Expression) extends Expression
case class Disjunction(e1 : Expression, e2 : Expression) extends Expression
case class LogicalNegation(e : Expression) extends Expression
case class Add(e1 : Expression, e2 : Expression) extends Expression
case class Subtract(e1 : Expression, e2 : Expression) extends Expression
case class Divide(e1 : Expression, e2 : Expression) extends Expression
case class Multiply(e1 : Expression, e2 : Expression) extends Expression
case class Equals(e1 : Expression, e2 : Expression) extends Expression
case class GreaterThan(e1 : Expression, e2 : Expression) extends Expression
case class GreaterThanOrEquals(e1 : Expression, e2 : Expression) extends Expression
case class LessThan(e1 : Expression, e2 : Expression) extends Expression
case class LessThanOrEquals(e1 : Expression, e2 : Expression) extends Expression
case class NotEquals(e1 : Expression, e2 : Expression) extends Expression
case class Dereference(e : Expression, f : String) extends Expression
case class LocalInvocation(name : String, args : Seq[Expression]) extends Expression
case class Invocation(recipient : Statement, name : String, args : Seq[Expression]) extends Expression
case class Construction(name : String, args : Seq[Expression]) extends Expression

/* statements and control flow constructs */
case class VariableDecl(typ : Type, varName : String) extends Statement
case object Return extends Statement
case class ReturnExpr(e : Expression) extends Statement
case class Transition(newStateName : String) extends Statement
case class Assignment(assignTo : Expression, e : Expression) extends Statement
case class Throw() extends Statement
case class If(eCond : Expression, s : Seq[Statement]) extends Statement
case class IfThenElse(eCond : Expression, s1 : Seq[Statement], s2 : Seq[Statement]) extends Statement
case class TryCatch(s1 : Seq[Statement], s2 : Seq[Statement]) extends Statement
case class Switch(e : Expression, cases : Seq[SwitchCase]) extends Statement
case class SwitchCase(stateName : String, body : Seq[Statement]) extends Statement

sealed trait TypeModifier
case object IsFinal extends TypeModifier
case object IsLinear extends TypeModifier
case object IsUnique extends TypeModifier
case object IsShared extends TypeModifier
case class Type(modifiers : Seq[TypeModifier], name : String) extends AST

/* Declarations */
case class TypeDecl(name : String, typ : Type) extends Declaration

case class FieldDecl(typ : Type, fieldName : String) extends Declaration

case class FuncDecl(name : String,
                           args : Seq[VariableDecl],
                           body : Seq[Statement]) extends Declaration
case class TransactionDecl(name : String,
                                  args : Seq[VariableDecl],
                                  body : Seq[Statement]) extends Declaration
case class StateDecl(name : String, declarations : Seq[Declaration]) extends Declaration
case class ContractDecl(name : String, declarations : Seq[Declaration]) extends AST
case class Program(contracts : Seq[ContractDecl]) extends AST