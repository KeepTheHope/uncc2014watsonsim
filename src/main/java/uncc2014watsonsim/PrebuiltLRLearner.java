package uncc2014watsonsim;
import java.util.*;

public class PrebuiltLRLearner extends Learner {
    /** Correlates search results for improved accuracy
     * It uses models prebuilt from Weka so it only makes sense to use this
     * implementation with Lucene and Indri*/
	public PrebuiltLRLearner() {}
	
	@Override
    public void test_implementation(Question question) {
    	for (Answer result : question) {
    		Document lucene = result.first("lucene");
    		Document indri = result.first("indri");
    		Document combined = new Document(result.getTitle(), result.getFullText(), null, "combined", 0, 0);
    		if (lucene != null && indri != null) {
    			combined.score = scoreBoth(indri.score, lucene.score);
    		} else if (lucene != null) {
    			combined.score = scoreLucene(lucene.score);
    		} else if (indri != null) {
    			combined.score = scoreIndri(indri.score);
    		}
    		// In any of the above three cases, but not the "else":
    		if (lucene != null || indri != null)
    			result.docs.add(combined);
    	}
    }
    
    double sigmoid(double x) {
    	return 1 / (1 + Math.exp(-x));
    }
    
    double scoreIndri(double score) {
    	return sigmoid(0.2115 * score - 1.4136);
    }
    double scoreLucene(double score) {
    	return sigmoid(0.2683 * score - 3.3227);
    }
    double scoreBoth(double indri, double lucene) {
    	return sigmoid(-0.1592 * indri
    			+ 0.4838 * lucene
				- 4.102);
    }
    
    class AnswerTitleComparator implements Comparator<Answer> {
    	public int compare(Answer a, Answer b) {
    		return a.getTitle().compareToIgnoreCase(b.getTitle());
    	}
    }
}
