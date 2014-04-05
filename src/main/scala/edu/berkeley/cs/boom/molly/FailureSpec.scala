package edu.berkeley.cs.boom.molly

import org.apache.commons.math3.util.ArithmeticUtils
import edu.berkeley.cs.boom.molly.ast.{Program, IntLiteral, StringLiteral, Predicate}
import edu.berkeley.cs.boom.molly.derivations.SATSolver.{MessageLoss, CrashFailure}

case class FailureSpec(
  eot: Int,
  eff: Int,
  maxCrashes: Int,
  nodes: List[String],
  crashes: Set[CrashFailure] = Set.empty,
  omissions: Set[MessageLoss] = Set.empty) {
  import FailureSpec._

  require(maxCrashes <= nodes.size, "Can't have more crashes than nodes")
  require(crashes.size <= maxCrashes, "Can't specify more than maxCrashes crashes")
  require(omissions.forall(_.time < eff), "Can't have omissions at or after the EFF")

  import ArithmeticUtils._

  def grossEstimate: Long = {
    val channels = (nodes.size * nodes.size) - nodes.size
    val lossScenarios = (0 to eff).map(i => binomialCoefficient(eff, i)).sum
    pow(lossScenarios, channels)
  }

  def generateClockFacts: Seq[Predicate] = {
    val temporalFacts = for (
      from <- nodes;
      to <- nodes;
      t <- 1 to eot
      if !crashes.exists(c => c.node == from && c.time <= t)  // if the sender didn't crash
    ) yield {
      val messageLost = omissions.exists(o => o.from == from && o.to == to && o.time == t)
      val deliveryTime = if (from != to && messageLost) NEVER else t + 1
      Predicate("clock", List(StringLiteral(from), StringLiteral(to), IntLiteral(t), IntLiteral(deliveryTime)), notin = false, None)
    }
    val localDeductiveFacts = for (node <- nodes; t <- 1 to eot) yield {
      Predicate("clock", List(StringLiteral(node), StringLiteral(node), IntLiteral(t), IntLiteral(t + 1)), notin = false, None)
    }
    val crashFacts = for (crash <- crashes; node <- nodes; t <- 1 to eot) yield {
      Predicate("crash", List(StringLiteral(node), StringLiteral(crash.node), IntLiteral(crash.time), IntLiteral(t)), notin = false, None)
    }
    localDeductiveFacts ++ temporalFacts ++ crashFacts
  }

  def addClockFacts(program: Program): Program = {
    program.copy(facts = program.facts ++ generateClockFacts)
  }
}


object FailureSpec {
  /**
   * Time at which lost messages are delivered.
   */
  val NEVER = 99999
}
