package edu.stanford.nlp.sempre.test;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 * Test parsers.
 *
 * @author Roy Frostig
 * @author Percy Liang
 */
public class ParserTest {
  // Collects a grammar, and some input/output test pairs
  public abstract static class ParseTest {
    public Grammar grammar;
    ParseTest(Grammar g) {
      this.grammar = g;
    }

    public Parser.Spec getParserSpec() {
      Executor executor = new JavaExecutor();
      FeatureExtractor extractor = new FeatureExtractor(executor);
      FeatureExtractor.opts.featureDomains.add("rule");
      ValueEvaluator valueEvaluator = new ExactValueEvaluator();
      return new Parser.Spec(grammar, extractor, executor, valueEvaluator);
    }

    public abstract void test(Parser parser);
  }

  private static void checkNumDerivations(Parser parser, Params params, String utterance, String targetValue, int numExpected) {
    Parser.opts.verbose = 5;
    Example ex = TestUtils.makeSimpleExample(utterance, targetValue != null ? Value.fromString(targetValue) : null);
    ParserState state = parser.parse(params, ex, targetValue != null);

    // Debug information
    for (Derivation deriv : state.predDerivations) {
      LogInfo.dbg(deriv.getAllFeatureVector());
      LogInfo.dbg(params.getWeights());
      LogInfo.dbgs("Score %f", deriv.computeScore(params));
    }
   // parser.extractor.extractLocal();
    assertEquals(numExpected, ex.getPredDerivations().size());
    if (numExpected > 0 && targetValue != null)
      assertEquals(targetValue, ex.getPredDerivations().get(0).value.toString());
  }
  private static void checkNumDerivations(Parser parser, String utterance, String targetValue, int numExpected) {
    checkNumDerivations(parser, new Params(), utterance, targetValue, numExpected);
  }

  static ParseTest ABCTest() {
    return new ParseTest(TestUtils.makeAbcGrammar()) {
      @Override
      public void test(Parser parser) {
        checkNumDerivations(parser, "a +", null, 0);
        checkNumDerivations(parser, "a", "(string a)", 1);
        checkNumDerivations(parser, "a b", "(string a,b)", 1);
        checkNumDerivations(parser, "a b c", "(string a,b,c)", 2);
        checkNumDerivations(parser, "a b c a b c", "(string a,b,c,a,b,c)", 42);
      }
    };
  }

  static ParseTest ArithmeticTest() {
    return new ParseTest(TestUtils.makeArithmeticGrammar()) {
      @Override
      public void test(Parser parser) {
        checkNumDerivations(parser, "1 + ", null, 0);
        checkNumDerivations(parser, "1 plus 2", "(number 3)", 1);
        checkNumDerivations(parser, "2 times 3", "(number 6)", 1);
        checkNumDerivations(parser, "1 plus times 3", null, 0);
        checkNumDerivations(parser, "times", null, 0);
      }
    };
  };

  // Create parsers
  @Test public void checkBeamNumDerivationsForABCGrammar() {
    Parser.opts.coarsePrune = false;
    ParseTest p;
    p = ABCTest();
    p.test(new BeamParser(p.getParserSpec()));
    p = ArithmeticTest();
    p.test(new BeamParser(p.getParserSpec()));
  }
  @Test public void checkCoarseBeamNumDerivations() {
    Parser.opts.coarsePrune = true;
    ParseTest p;
    p = ABCTest();
    p.test(new BeamParser(p.getParserSpec()));
    p = ArithmeticTest();
    p.test(new BeamParser(p.getParserSpec()));
  }

  @Test(groups = "reinforcement") public void checkReinforcementNumDerivations() {
    ParseTest p;
    p = ABCTest();
    p.test(new ReinforcementParser(p.getParserSpec()));
    p = ArithmeticTest();
    p.test(new ReinforcementParser(p.getParserSpec()));
    // TODO(chaganty): test more thoroughly
  }

  @Test public void checkFloatingNumDerivations() {
    // Make it behave like the BeamParser
    FloatingParser.opts.defaultIsFloating = false;
    ParseTest p;
    p = ABCTest();
    p.test(new FloatingParser(p.getParserSpec()));
    p = ArithmeticTest();
    p.test(new FloatingParser(p.getParserSpec()));

    // If floating, should get more hypotheses
    FloatingParser.opts.defaultIsFloating = true;
    Parser parser = new FloatingParser(ABCTest().getParserSpec());
    FloatingParser.opts.maxDepth = 2;
    checkNumDerivations(parser, "ignore", null, 3);
    FloatingParser.opts.maxDepth = 3;
    checkNumDerivations(parser, "ignore", null, 3 + 3 * 3);
  }

  // TODO(chaganty): verify that things are ranked appropriately
  public void checkRankingArithmetic(Parser parser) {
    Params params = new Params();
    TObjectDoubleMap<String> features = new TObjectDoubleHashMap<>();
    features.put("rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call + (var x) (var y)))))", 1.0);
    features.put("rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call * (var x) (var y)))))", -1.0);
    params.update(features);
    checkNumDerivations(parser, params, "2 and 3", "(number 5)", 2);

    params = new Params();
    features.put("rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call + (var x) (var y)))))", -1.0);
    features.put("rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call * (var x) (var y)))))", 1.0);
    params.update(features);
    checkNumDerivations(parser, params, "2 and 3", "(number 6)", 2);
  }
  @Test void checkRankingSimple() {
    checkRankingArithmetic(new BeamParser(ArithmeticTest().getParserSpec()));
  }
  @Test void checkRankingReinforcement() {
    checkRankingArithmetic(new ReinforcementParser(ArithmeticTest().getParserSpec()));
  }
  @Test void checkRankingFloating() {
    FloatingParser.opts.defaultIsFloating = false;
    checkRankingArithmetic(new FloatingParser(ArithmeticTest().getParserSpec()));
  }

  // TODO(chaganty): verify the parser gradients


}
