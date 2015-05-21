package edu.uncc.cs.watsonsim.nlp;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.search.ScoreDoc;

import edu.uncc.cs.watsonsim.Database;
import edu.uncc.cs.watsonsim.Environment;
import edu.uncc.cs.watsonsim.Phrase;
import edu.uncc.cs.watsonsim.StringUtils;

public class Synonyms {
	private final Database db;
	private final PreparedStatement link_statement;
	private final Environment env;
	/**
	 * Create a Synonyms module using shared resources. 
	 * @param env
	 */
	public Synonyms(Environment env) {
		this.db = env.db;
		this.env = env;
		/*
		 * It's possible to send arrays of keys instead but the syntax is not
		 * consistent across PSQL and SQLite so I'm issuing many small queries.
		 * Postgres -> "WHERE link = ANY (?)"
		 * SQLite   -> "WHERE link IN (?)"
		 * So there may be real overhead.
		 */
		link_statement = db.prep(
				"SELECT count(*), trim_target"
				+ " FROM wiki_links"
				+ " WHERE link = ?"
				+ " GROUP BY trim_target HAVING count(*) > 1" 
				+ " ORDER BY count(*) DESC;");
	}
	
	/**
	 * Match using Wiki redirects
	 */
	public boolean matchViaRedirect(String left, String right) {
		try {
			PreparedStatement stmt = db.prep(
					"SELECT count(*) FROM wiki_redirects WHERE "
					+ "(source=? AND target=?) OR (source=? AND target=?);");
			stmt.setString(1, left);
			stmt.setString(2, right);
			stmt.setString(3, left);
			stmt.setString(4, right);
			return db.then(stmt).getInt(1) > 0;
		} catch (SQLException e) {
			return false;
		}
	}
	
	/**
	 * Find paraphrases and synonyms of a set of phrases.
	 * You can enter multiple sources, which are an array for syntactic
	 * convenience. The scoring will be combined between all the sources.
	 * The exact scoring method may change over time.
	 * 
	 * @param sources an array of words for which you want synonyms
	 */
	public List<Weighted<String>> viaWikiLinks(String[] sources) {
		try {
			List<Weighted<String>> synonyms = new ArrayList<>();
			for (String source : sources){
				link_statement.setString(1, source);
				ResultSet rows = link_statement.executeQuery();
				while (rows.next()) {
					synonyms.add(new Weighted<>(rows.getString(2), rows.getDouble(1)));
				}
			}
			return synonyms;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to create query for wiki link synonyms of \"" + sources + "\"");
		}
	}
	
	public boolean matchViaSearch(String left, String right) {
		final int K = 3;
		final int Q = K/2;
		ScoreDoc[] left_hits = env.simpleLuceneQuery(left, K);
		Set<ScoreDoc> lefts = new HashSet<>(Arrays.asList(left_hits));
		ScoreDoc[] right_hits = env.simpleLuceneQuery(right, K);
		Set<ScoreDoc> rights = new HashSet<>(Arrays.asList(right_hits));
		return left_hits.length >0 && right_hits.length > 0 && left_hits[0].doc == right_hits[0].doc;
		//lefts.retainAll(rights);
		//return lefts.size() > Q;
	}
	
	
	/**
	 * This is a very strict way of measuring synonymy, simply by the edit
	 * distance. It does handle a few rudimentary similarities, however.
	 * 
	 * First it canonicalizes the inputs (see StringUtils.canonicalize),
	 * then it will
	 *    ignore up to one letter of edit distance.
	 *    	  This helps for situations like Advertize = Advertise 
	 * 
	 * This is used by the grading scorer (CORRECT) so keep that in mind.
	 * 
	 * @return Whether the two strings are synonymous.
	 */
	public boolean matchViaLevenshtein(String left, String right) {
		int dist = StringUtils.getLevenshteinDistance(
				StringUtils.canonicalize(left),
				StringUtils.canonicalize(right),
				2);
        // -1 means "uncertain, but greater than the threshold"
		return (0 <= dist && dist < 2);
	}
	
	public boolean implies(Phrase left, Phrase right) {
		return matchViaLevenshtein(left.text, right.text)
				|| matchViaSearch(left.text, right.text)
				|| matchViaRedirect(left.text, right.text)
				|| right.memo(Phrase::lemmas).containsAll(left.memo(Phrase::lemmas))
				|| StringUtils.containsIgnoreCase(right.text, left.text);
	}
	
}
