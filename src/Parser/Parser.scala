package Parser

import Lexer._

import scala.collection._
import scala.util.parsing.combinator._

/*

The following sort of expression grammar is needed to eliminate left recursion
and allow operators to have the appropriate precedence

E ::= E9
E9 ::= E8 and E9 | E8
E8 ::= E7 or E8 | E7
E7 ::= E6 == E7 | E6 != E7 | E6 < E7 | E6 > E7 | E6 <= E7 | E6 >= E7 | E6
E6 ::= E5 + E6 | E5
E5 ::= E4 - E5 | E4
E4 ::= E3 * E4 | E3
E3 ::= E2 / E3 | E2
E2 ::= not E1 | E1
E1 ::= ( E ) | n | x | true | false

 */

object Parser extends Parsers {
    override type Elem = Token

    private def parseIdString : Parser[String] = {
        accept("identifier", { case IdentifierT(name) => name })
    }

    private def parseTypeModifier = {
        val linearP = LinearT() ^^ (_ => IsLinear)
        val finalP = FinalT() ^^ (_ => IsFinal)
        val uniqueP = UniqueT() ^^ (_ => IsUnique)
        val sharedP = SharedT() ^^ (_ => IsShared)

        linearP | finalP | uniqueP | sharedP
    }

    private def parseType = rep(parseTypeModifier) ~ parseIdString ^^ {
        case mods ~ id => Type(mods, id)
    }

    private def parseArgList : Parser[Seq[Expression]] = repsep(parseExpr, CommaT())

    private def parseArgDefList : Parser[Seq[VariableDecl]] = {
        val oneDecl = parseType ~ parseIdString ^^ {
            case typ ~ name => VariableDecl(typ, name)
        }
        repsep(oneDecl, CommaT())
    }

    private def parseBody : Parser[Seq[Statement]] =
        parseAtomicStatement ~ opt(parseBody) ^^ {
            case s ~ None => s
            case s1 ~ Some(s2) => s1 ++ s2
        }

    /* this parser is for Seq[AST] instead of AST to handle declaration
     *  and assignment of a variable in a single statement */
    private def parseAtomicStatement : Parser[Seq[Statement]] = {
        val parseReturn = ReturnT() ~ opt(parseExpr) ~! SemicolonT() ^^ {
            case _ ~ Some(e) ~ _ => ReturnExpr(e)
            case _ ~ None ~ _ => Return
        }

        val parseTransition = RightArrowT() ~ parseIdString ~! SemicolonT() ^^ {
            case _ ~ name ~ _ => Transition(name)
        }

        val parseVarDeclAssn =
            parseType ~ parseIdString ~ EqT() ~! parseExpr ~! SemicolonT() ^^ {
                case typ ~ name ~ _ ~ e ~ _ =>
                    Seq(VariableDecl(typ, name), Assignment(Variable(name), e))
        }

        val parseVarDecl =
            parseType ~ parseIdString ~! SemicolonT() ^^ {
                case typ ~ name ~ _ => VariableDecl(typ, name)
            }

        val assign = EqT() ~! parseExpr ^^ {
            case _ ~ e2 => (e1 : Expression) => Assignment(e1, e2)
        }

        val parseThrow = ThrowT() ~! SemicolonT() ^^ { case _ => Throw() }

        val parseOnlyIf = IfT() ~! parseExpr ~! LBraceT() ~! parseBody ~! RBraceT()
        val parseElse = ElseT() ~! LBraceT() ~! parseBody ~! RBraceT()

        val parseIf = parseOnlyIf ~ opt(parseElse) ^^ {
            case _ ~ e ~ _ ~ s ~ _ ~ None => If(e, s)
            case _ ~ e ~ _ ~ s1 ~ _ ~ Some(_ ~ _ ~ s2 ~ _) => IfThenElse(e, s1, s2)
        }

        val parseTryCatch = TryT() ~! LBraceT() ~! parseBody ~! RBraceT() ~!
                            CatchT() ~! LBraceT() ~! parseBody <~ RBraceT() ^^ {
            case _ ~ _ ~ s1 ~ _ ~ _ ~ _ ~ s2 => TryCatch(s1, s2)
        }

        val parseCase = CaseT() ~! parseIdString ~! LBraceT() ~! parseBody ~! RBraceT() ^^ {
            case _ ~ name ~ _ ~ body ~ _ => SwitchCase(name, body)
        }

        val parseSwitch =
            SwitchT() ~! parseExpr ~! LBraceT() ~! rep(parseCase) ~! RBraceT() ^^ {
                case _ ~ e ~ _ ~ cases ~ _ => Switch(e, cases)
        }

        /* allow arbitrary expr as a statement and then check at later stage if
         * the expressions makes sense as the recipient of an assignment or
         * as a side-effect statement (e.g. func invocation) */
        val parseExprFirst = {
            parseExpr ~ opt(assign) ~ SemicolonT() ^^ {
                case e ~ Some(assn) ~ _ => assn(e)
                case e ~ None ~ _ => e
            }
        }

        val seqify = (p : Parser[Statement]) => p ^^ { case a => Seq(a) }

        seqify(parseReturn) | seqify(parseTransition) | seqify(parseThrow) |
        parseVarDeclAssn | seqify(parseVarDecl) | seqify(parseIf) | seqify(parseSwitch) |
        seqify(parseTryCatch) | seqify(parseExprFirst)
    }


    /* this is a lot better than manually writing code for all the AST operators */
    private def parseBinary(t : Token,
                            makeExpr : (Expression, Expression) => Expression,
                            nextParser : Parser[Expression]
                           ) : Parser[Expression] = {
        val hasOpParser = t ~ parseBinary(t, makeExpr, nextParser) ^^ {
            case _ ~ e => e
        }

        nextParser ~ opt(hasOpParser) ^^ {
            case e ~ None => e
            case e1 ~ Some(e2) => makeExpr(e1, e2)
        }
    }

    private def parseUnary(t : Token,
                   makeExpr : Expression => Expression,
                   nextParser : Parser[Expression]
                  ) : Parser[Expression] = {
        val hasOpParser = t ~ parseUnary(t, makeExpr, nextParser) ^^ {
            case _ ~ e => makeExpr(e)
        }

        hasOpParser | nextParser
    }

    private def parseExpr = parseAnd
    private def parseAnd = parseBinary(AndT(), Conjunction.apply, parseOr)
    private def parseOr = parseBinary(OrT(), Disjunction.apply, parseEq)

    private def parseEq = parseBinary(EqEqT(), Equals.apply, parseNeq)
    private def parseNeq = parseBinary(NotEqT(), NotEquals.apply, parseGt)
    private def parseGt = parseBinary(GtT(), GreaterThan.apply, parseLt)
    private def parseLt = parseBinary(LtT(), LessThan.apply, parseLtEq)
    private def parseLtEq = parseBinary(LtEqT(), LessThanOrEquals.apply, parseGtEq)
    private def parseGtEq = parseBinary(GtEqT(), GreaterThanOrEquals.apply, parseAddition)

    private def parseAddition = parseBinary(PlusT(), Add.apply, parseSubtraction)
    private def parseSubtraction = parseBinary(MinusT(), Subtract.apply, parseMultiplication)
    private def parseMultiplication = parseBinary(StarT(), Multiply.apply, parseDivision)
    private def parseDivision = parseBinary(ForwardSlashT(), Divide.apply, parseNot)
    private def parseNot = parseUnary(NotT(), LogicalNegation.apply, parseExprBottom)


    /* parsing of invocations and dereferences is used in both statements and expressions */

    private def parseLocalInv = {
        parseIdString ~ LParenT() ~ parseArgList ~ RParenT() ^^ {
            case name ~ _ ~ args ~ _ => LocalInvocation(name, args)
        }
    }

    /* avoids left recursion by parsing from the dot, e.g. ".f(a)", not "x.f(a)" */

    type DotExpr = Either[String, (String, Seq[Expression])]

    private def foldDotExpr(e : Expression, dots : Seq[DotExpr]) : Expression = {
        dots.foldLeft(e)(
            (e : Expression, inv : DotExpr) => inv match {
                case Left(fieldName) => Dereference(e, fieldName)
                case Right((funcName, args)) => Invocation(e, funcName, args)
            }
        )
    }

    private def parseDots : Parser[Expression => Expression] = {
        val parseOne = DotT() ~! parseIdString ~ opt(LParenT() ~ parseArgList ~ RParenT()) ^^ {
            case _ ~ name ~ Some(_ ~ args ~ _) => Right((name, args))
            case _ ~ name ~ None => Left(name)
        }

        rep(parseOne) ^^ {
            case lst => (e : Expression) => foldDotExpr(e, lst)
        }
    }

    private def parseExprBottom : Parser[Expression] = {
        val parenExpr = LParenT() ~! parseExpr ~! RParenT() ^^ {
            case _ ~ e ~ _ => e
        }

        val parseVar = parseIdString ^^ { Variable(_) }

        val parseNumLiteral = {
            accept("numeric literal", { case NumLiteralT(n) => NumLiteral(n) })
        }

        val parseNew = {
            NewT() ~! parseIdString ~! LParenT() ~! parseArgList ~! RParenT() ^^ {
                case _ ~ name ~ _ ~ args ~ _ => Construction(name, args)
            }
        }

        val fail = failure("expression expected")

        val simpleExpr = parseNew | parseLocalInv | parseNumLiteral | parseVar | parenExpr | fail

        simpleExpr ~ parseDots ^^ { case e ~ applyDots => applyDots(e) }
    }

    private def parseFieldDecl = {
        parseType ~ parseIdString ~! SemicolonT() ^^ {
            case typ ~ name ~ _ => FieldDecl(typ, name)
        }
    }

    private def parseFuncDecl = {
        FunctionT() ~! parseIdString ~! LParenT() ~! parseArgDefList ~! RParenT() ~!
        LBraceT() ~! parseBody ~! RBraceT() ^^ {
            case _ ~ name ~ _ ~ args ~ _ ~ _ ~ body ~ _ => FuncDecl(name, args, body)
        }
    }

    private def parseTransDecl = {
        TransactionT() ~! parseIdString ~! LParenT() ~! parseArgDefList ~! RParenT() ~!
        LBraceT() ~! parseBody ~! RBraceT() ^^ {
            case _ ~ name ~ _ ~ args ~ _ ~ _ ~ body ~ _ => TransactionDecl(name, args, body)
        }
    }

    private def parseStateDecl = {
        StateT() ~! parseIdString ~! LBraceT() ~! rep(parseDecl) ~! RBraceT() ^^ {
            case _ ~ name ~ _ ~ defs ~ _ => StateDecl(name, defs)
        }
    }

    private def parseDecl : Parser[Declaration] = {
        parseFieldDecl | parseFuncDecl | parseTransDecl | parseStateDecl
    }

    private def parseContractDecl = {
        ContractT() ~! parseIdString ~! LBraceT() ~! rep(parseDecl) ~! RBraceT() ^^ {
            case _ ~ name ~ _ ~ defs ~ _ => ContractDecl(name, defs)
        }
    }

    private def parseProgram = {
        phrase(rep1(parseContractDecl)) ^^ { Program(_) }
    }

    def parseAST(tokens : Seq[Token]) : Either[String, AST] = {
        val reader = new TokenReader(tokens)
        parseProgram(reader) match {
            case Success(result, _) => Right(result)
            case Failure(msg , _) => Left(s"FAILURE: $msg")
            case Error(msg , next) => {
                val line = next.first.pos.line
                val col = next.first.pos.column
                Left(s"Error: `$msg at $line:$col")
            }
        }
    }
}