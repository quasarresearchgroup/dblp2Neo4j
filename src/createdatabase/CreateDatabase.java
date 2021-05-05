package createdatabase;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CreateDatabase {

//    private static String url = "jdbc:neo4j:http://localhost";
   private static String url = "jdbc:neo4j:bolt://localhost:7687";
    private static Connection conn = null;
	
	private static Boolean clearDatabase = true;  //Clears the database before inserting new data.
    
	private static Boolean insertArticles      = true;  //Inserts articles.
	private static Boolean insertIncollections = true;  //Inserts incollections.
	private static Boolean insertInproceedings = true;  //Inserts inproceedings.
	private static Boolean insertBooks         = true;  //Inserts books.
	private static Boolean insertProceedings   = true;  //Inserts proceedings.
	private static Boolean insertPhdtheses     = true;  //Inserts phd theses.
	private static Boolean insertMasterstheses = true;  //Inserts master's theses.

	private static Boolean insertXml           = true;  //Inserts data from xml files.
	
	private static Boolean findMostUsedKeywords   = false;  //Finds the most used keywords by searching the titles that were inserted in the database.
    
	private static final int KEYWORD_ARRAY_LENGTH = 200; //The ammount of keywords that will be added in the database.
	private static final int MIN_KEYWORD_LENGTH   = 5;   //The minimum length of a keyword that will be added in the database.
    
    
	/**
	 * @param args
	 */
	public static void main(String[] args) {

        try {
        	Connection conn = DriverManager.getConnection(url, "neo4j", "Mafalda2021");
        } catch (SQLException e) {
            System.err.println("ERROR: "+ e.getClass().getName()+": "+e.getMessage());
            System.exit(0);
        }
        
        try {
            System.setProperty("entityExpansionLimit", "2000000");
                
            if (clearDatabase) {
                editQuery("match (n) optional match (n)-[r]-() delete n,r");
                
                // Shouldn't it be the following?: 
                // MATCH (n) DETACH DELETE n
                // see https://neo4j.com/docs/cypher-manual/current/clauses/delete/#delete-delete-all-nodes-and-relationships
            }
 
            if (insertXml) {
                File fXmlFile = new File("data/dblp.xml");
                insertXmlFile(fXmlFile);
            }
            
//            if (insertXml) {
//                int k=0;
//                File fXmlFile = new File("dblp xmls/dblp"+k+".xml");
//                //Insert xmls in the database starting from dblp0.xml (if k equals 0) and continuing with dblp1.xml, dblp2.xml etc.
//                while(fXmlFile.exists()) {
//                    insertXmlFile(fXmlFile);
//
//                    k++;
//                    fXmlFile = new File("dblp xmls/dblp"+k+".xml");
//                }
//            }
            
            if (findMostUsedKeywords) {
            	findMostUsedKeywords(KEYWORD_ARRAY_LENGTH, MIN_KEYWORD_LENGTH);
            }
            
        } catch (IOException | ParserConfigurationException | DOMException | SAXException | SQLException e) {
            e.printStackTrace(System.out);
        }

        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println("ERROR: "+ e.getClass().getName()+": "+e.getMessage());
            System.exit(0);
        }
    }



	/**
	 * @param fXmlFile
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws SQLException
	 */
	private static void insertXmlFile(File fXmlFile)
			throws ParserConfigurationException, SAXException, IOException, SQLException {
		System.out.println("Inserting "+fXmlFile.getName());
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(fXmlFile);
		doc.getDocumentElement().normalize();
		
		//The parent node that all the publications are connected to.
		editQuery("create (n:publication {title:'Publication'})");
		
		if (insertArticles) {
		    insertArticles(doc);
		}
		                                       
		if (insertIncollections) {
		    insertIncollections(doc);
		}
		
		if (insertInproceedings) {
		    insertInproceedings(doc);
		}

		if (insertBooks) {
		    insertBooks(doc);
		}
		
		if (insertProceedings) {
		    insertProceedings(doc);
		}
		
		if (insertPhdtheses){
		    insertPhdtheses(doc);
		}
		
		if (insertMasterstheses) {
		    insertMasterstheses(doc);
		}
	}
	


    /**
     * @param queryStr
     * @return
     * @throws SQLException
     */
    private static ResultSet selectQuery(String queryStr) throws SQLException{
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(queryStr);
        return rs;
    }
    
    /**
     * @param queryStr
     * @throws SQLException
     */
    private static void editQuery(String queryStr) throws SQLException{
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(queryStr);
        }
    }
    
	/**
	 * Finds the most used keywords and inserts them in the database. The keywords are inserted in their singular form.
	 * @param KEYWORD_ARRAY_LENGTH
	 * @param MIN_KEYWORD_LENGTH
	 * @param inf
	 * @throws SQLException
	 */
	private static void findMostUsedKeywords(final int KEYWORD_ARRAY_LENGTH, final int MIN_KEYWORD_LENGTH) throws SQLException {

		Inflector inf = new Inflector();
		ResultSet rs;
		
		System.out.println("Finding most used keywords...");
		rs = selectQuery("match (a)-[:IS]-(b:publication) return a.title");
		
		ArrayList<KeywordRating> mostUsedKeywords = new ArrayList<>();
		for (int i=0; i<KEYWORD_ARRAY_LENGTH;i++) {
		    mostUsedKeywords.add(i,new KeywordRating());
		}
		
		while (rs.next()) {
		    String title = rs.getString("a.title").trim();
		    String[] keywords = title.split(" ");
		    
		    //Changes all the keywords to lower case and singular form. Also removes .,: characters that may be at the end of a keyword.
		    for (int i=0; i<keywords.length; i++) {
		        keywords[i] = keywords[i].replaceAll("[.,:()]", "");
		        keywords[i] = keywords[i].toLowerCase();
		        keywords[i] = inf.singularize(keywords[i]);
		    }
		    
		    for (String keyword : keywords) {
		        //System.out.println(keyword);
		        if (keyword.length()>MIN_KEYWORD_LENGTH-1) {
		            Boolean keywordExists = false;
		            for (int i=0; i<KEYWORD_ARRAY_LENGTH; i++) {
		                if (mostUsedKeywords.get(i).getKeyword().equals(keyword)) {
		                    keywordExists = true;
		                }
		            }
		            
		            if (!keywordExists) {
		                ResultSet rs2 = selectQuery("match (a)-[:IS]-(b:publication) WHERE toLower(a.title) CONTAINS '"+keyword+"' OR toLower(a.title) CONTAINS '"+inf.pluralize(keyword)+"' return count(a) as timesFound");
		                if (rs2.next()) {
		                    int timesFound = rs2.getInt("timesFound");
		                    
		                    if (timesFound > mostUsedKeywords.get(0).getTimesFound()) {
		                        mostUsedKeywords.set(0,new KeywordRating(keyword,timesFound));
		                        Collections.sort(mostUsedKeywords);  
		                    }
		                }
		            }
		        }
		    }
		}
		
		editQuery("match (n:keyword) detach delete n");
		for (int i=0; i<KEYWORD_ARRAY_LENGTH; i++) {
		    String keyword = mostUsedKeywords.get(i).getKeyword();
		    if (!keyword.equals("-")) {
		        int timesFound = mostUsedKeywords.get(i).getTimesFound();   
		        editQuery("create (n:keyword {keyword:'"+keyword+"', timesFound:"+timesFound+"})");
		    }
		}
	}

	/**
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertMasterstheses(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("mastersthesis");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent();
		        String school = eElement.getElementsByTagName("school").item(0).getTextContent();
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:mastersthesis {title:'"+title+"', year:'"+year+"', key:'"+key+"', school:'"+school+"'})");
		        editQuery("match (a:mastersthesis {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the proceedings in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("author").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("author").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:mastersthesis {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		    }
		}
	}

	/**
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertPhdtheses(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("phdthesis");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent();
		        String school = eElement.getElementsByTagName("school").item(0).getTextContent();
		        
		        String publisher = "-";
		        if (eElement.getElementsByTagName("publisher").getLength()>0) {
		            publisher = eElement.getElementsByTagName("publisher").item(0).getTextContent();
		            publisher = publisher.replaceAll("[\\\\'/%$\"]", "");
		        }
		        
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String series = "-";
		        if (eElement.getElementsByTagName("series").getLength()>0) {
		            series = eElement.getElementsByTagName("series").item(0).getTextContent();
		        }
		        
		        String volume = "-";
		        if (eElement.getElementsByTagName("volume").getLength()>0) {
		            volume = eElement.getElementsByTagName("volume").item(0).getTextContent();
		        }
		        
		        String isbn = "-";
		        if (eElement.getElementsByTagName("isbn").getLength()>0) {
		            isbn = eElement.getElementsByTagName("isbn").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:phdthesis {title:'"+title+"', year:'"+year+"', key:'"+key+"', school:'"+school+"'})");
		        editQuery("match (a:phdthesis {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the phdthesis in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("author").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("author").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:phdthesis {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //Inserts the publisher of the phdthesis in the database, if the publisher exists.
		        if (!publisher.equals("-")) {
		            rs = selectQuery("match (n:publisher {name:'"+publisher+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:publisher {name: '"+publisher+"'})");
		            }
		            editQuery("match (a:publisher {name:'"+publisher+"'}), (b:phdthesis {key:'"+key+"'}) merge (a)-[r:PUBLISHED]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.month='"+pages+"'");            
		        }
		        
		        //Adds the series property to the node.
		        if (!series.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.series='"+series+"'");            
		        }
		        
		        //Adds the volume property to the node.
		        if (!volume.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.volume='"+volume+"'");            
		        }
		        
		        //Adds the isbn property to the node.
		        if (!isbn.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.isbn='"+isbn+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:phdthesis {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	}

	/**
	 * Inserts proceedings in the database
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertProceedings(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("proceedings");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent();
		        
		        String publisher = "-";
		        if (eElement.getElementsByTagName("publisher").getLength()>0) {
		            publisher = eElement.getElementsByTagName("publisher").item(0).getTextContent();
		            publisher = publisher.replaceAll("[\\\\'/%$\"]", "");
		        }
		            
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String series = "-";
		        if (eElement.getElementsByTagName("series").getLength()>0) {
		            series = eElement.getElementsByTagName("series").item(0).getTextContent();
		        }
		        
		        String volume = "-";
		        if (eElement.getElementsByTagName("volume").getLength()>0) {
		            volume = eElement.getElementsByTagName("volume").item(0).getTextContent();
		        }
		        
		        String isbn = "-";
		        if (eElement.getElementsByTagName("isbn").getLength()>0) {
		            isbn = eElement.getElementsByTagName("isbn").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:proceedings {title:'"+title+"', year:'"+year+"', key:'"+key+"'})");
		        editQuery("match (a:proceedings {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the proceedings in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("editor").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("editor").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:proceedings {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //Insert the publisher of the proceedings in the database.
		        if (!publisher.equals("-")) {
		            rs = selectQuery("match (n:publisher {name:'"+publisher+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:publisher {name:'"+publisher+"'})");
		            }
		            editQuery("match (a:publisher {name:'"+publisher+"'}), (b:proceedings {key:'"+key+"'}) merge (a)-[r:PUBLISHED]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.month='"+pages+"'");            
		        }
		        
		        //Adds the series property to the node.
		        if (!series.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.series='"+series+"'");            
		        }
		        
		        //Adds the volume property to the node.
		        if (!volume.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.volume='"+volume+"'");            
		        }
		        
		        //Adds the isbn property to the node.
		        if (!isbn.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.isbn='"+isbn+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:proceedings {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	}

	/**
	 * Inserts books in the database
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertBooks(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("book");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent();
		        
		        String publisher = "-";
		        if (eElement.getElementsByTagName("publisher").getLength()>0) {
		            publisher = eElement.getElementsByTagName("publisher").item(0).getTextContent();
		            publisher = publisher.replaceAll("[\\\\'/%$\"]", "");
		        }
		        
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String series = "-";
		        if (eElement.getElementsByTagName("series").getLength()>0) {
		            series = eElement.getElementsByTagName("series").item(0).getTextContent();
		        }
		        
		        String volume = "-";
		        if (eElement.getElementsByTagName("volume").getLength()>0) {
		            volume = eElement.getElementsByTagName("volume").item(0).getTextContent();
		        }
		        
		        String isbn = "-";
		        if (eElement.getElementsByTagName("isbn").getLength()>0) {
		            isbn = eElement.getElementsByTagName("isbn").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:book {title:'"+title+"', year:'"+year+"', key:'"+key+"'})");
		        editQuery("match (a:book {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the book in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("editor").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("editor").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:book {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //Insert the publisher of the book in the database.
		        if (!publisher.equals("-")) {
		            rs = selectQuery("match (n:publisher {name:'"+publisher+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:publisher {name:'"+publisher+"'})");
		            }
		            editQuery("match (a:publisher {name:'"+publisher+"'}), (b:book {key:'"+key+"'}) merge (a)-[r:PUBLISHED]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.month='"+pages+"'");            
		        }
		        
		        //Adds the series property to the node.
		        if (!series.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.series='"+series+"'");            
		        }
		        
		        //Adds the volume property to the node.
		        if (!volume.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.volume='"+volume+"'");            
		        }
		        
		        //Adds the isbn property to the node.
		        if (!isbn.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.isbn='"+isbn+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:book {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	}

	/**
	 * Inserts inproceedings in the database
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertInproceedings(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("inproceedings");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String booktitle = eElement.getElementsByTagName("booktitle").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent(); 
		            
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        booktitle = booktitle.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:inproceedings {title:'"+title+"', booktitle:'"+booktitle+"', year:'"+year+"', key:'"+key+"'})");
		        editQuery("match (a:inproceedings {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the inproceedings in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("author").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("author").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:inproceedings {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:inproceedings {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:inproceedings {key:'"+key+"'}) set b.pages='"+pages+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:inproceedings {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	}

	/**
	 * Inserts incollections in the database
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertIncollections(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("incollection");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);
		    
		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String booktitle = eElement.getElementsByTagName("booktitle").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent(); 
		            
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        booktitle = booktitle.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:incollection {title:'"+title+"', booktitle:'"+booktitle+"', year:'"+year+"', key:'"+key+"'})");
		        editQuery("match (a:incollection {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the incollection in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("author").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("author").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:incollection {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:incollection {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:incollection {key:'"+key+"'}) set b.pages='"+pages+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:incollection {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	}

	/**
	 * Inserts articles in the database
	 * @param doc
	 * @throws SQLException
	 */
	private static void insertArticles(Document doc) throws SQLException {
		ResultSet rs;
		NodeList nList = doc.getElementsByTagName("article");
		for (int temp = 0; temp < nList.getLength(); temp++) {
		    Node nNode = nList.item(temp);

		    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		        Element eElement = (Element) nNode;
		        String key = eElement.getAttribute("key");
		        String title = eElement.getElementsByTagName("title").item(0).getTextContent();
		        String year = eElement.getElementsByTagName("year").item(0).getTextContent();
		        String journal = eElement.getElementsByTagName("journal").item(0).getTextContent();
		        journal = journal.replaceAll("[\\\\'/%$\"]", "");
		        
		        String publisher = "-";
		        if (eElement.getElementsByTagName("publisher").getLength()>0) {
		            publisher = eElement.getElementsByTagName("publisher").item(0).getTextContent();
		            publisher = publisher.replaceAll("[\\\\'/%$\"]", "");
		        }
		        
		        String month = "-";
		        if (eElement.getElementsByTagName("month").getLength()>0) {
		            month = eElement.getElementsByTagName("month").item(0).getTextContent();
		        }
		        
		        String pages = "-";
		        if (eElement.getElementsByTagName("pages").getLength()>0) {
		            pages = eElement.getElementsByTagName("pages").item(0).getTextContent();
		        }
		        
		        String volume = "-";
		        if (eElement.getElementsByTagName("volume").getLength()>0) {
		            volume = eElement.getElementsByTagName("volume").item(0).getTextContent();
		        }
		        
		        String number = "-";
		        if (eElement.getElementsByTagName("number").getLength()>0) {
		            number = eElement.getElementsByTagName("number").item(0).getTextContent();
		        }
		        
		        String ee = "-";
		        if (eElement.getElementsByTagName("ee").getLength()>0) {
		            ee = eElement.getElementsByTagName("ee").item(0).getTextContent();
		        }
		        
		        title = title.replaceAll("[\\\\'/%$\"]", "");
		        editQuery("create (n:article {title:'"+title+"', year:'"+year+"', key:'"+key+"'})");
		        editQuery("match (a:article {key:'"+key+"'}),(b:publication) merge (a)-[r:IS]->(b)");
		        
		        //Inserts the authors of the article in the database.
		        String[] authors = new String[100];                      
		        for (int i=0; i<eElement.getElementsByTagName("author").getLength(); i++) {
		            authors[i] = eElement.getElementsByTagName("author").item(i).getTextContent();
		            authors[i] = authors[i].replaceAll("[\\\\'/%$\"]", "");
		            
		            rs = selectQuery("match (n:author {name:'"+authors[i]+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:author {name:'"+authors[i]+"'})");
		            }
		            editQuery("match (a:author {name:'"+authors[i]+"'}), (b:article {key:'"+key+"'}) merge (a)-[r:WROTE]->(b)");
		        }
		        
		        //Inserts the journal of the article in the database.
		        rs = selectQuery("match (n:journal {name:'"+journal+"'}) return n.name");                          
		        if (!rs.next()) {
		            editQuery("create (n:journal {name:'"+journal+"'})");
		        }
		        editQuery("match (a:journal {name:'"+journal+"'}), (b:article {key:'"+key+"'}) merge (a)-[r:INCLUDES]->(b)"); 
		        
		        //Inserts the publisher of the article in the database, if the publisher exists.
		        if (!publisher.equals("-")) {
		            rs = selectQuery("match (n:publisher {name:'"+publisher+"'}) return n.name");                          
		            if (!rs.next()) {
		                editQuery("create (n:publisher {name: '"+publisher+"'})");
		            }
		            editQuery("match (a:publisher {name:'"+publisher+"'}), (b:article {key:'"+key+"'}) merge (a)-[r:PUBLISHED]->(b)");
		        }
		        
		        //All the properties below are only added if they exist.
		        //Adds the month property to the node.
		        if (!month.equals("-")) {
		            editQuery("match (b:article {key:'"+key+"'}) set b.month='"+month+"'");            
		        }
		        
		        //Adds the pages property to the node.
		        if (!pages.equals("-")) {
		            editQuery("match (b:article {key:'"+key+"'}) set b.pages='"+pages+"'");            
		        }
		        
		        //Adds the volume property to the node.
		        if (!volume.equals("-")) {
		            editQuery("match (b:article {key:'"+key+"'}) set b.volume='"+volume+"'");            
		        }
		        
		        //Adds the number property to the node.
		        if (!number.equals("-")) {
		            editQuery("match (b:article {key:'"+key+"'}) set b.number='"+number+"'");            
		        }
		        
		        //Adds the ee (electronic encyclopedia) property to the node.
		        if (!ee.equals("-")) {
		            editQuery("match (b:article {key:'"+key+"'}) set b.ee='"+ee+"'");            
		        }
		    }
		}
	} 
}
