package tutorial;

import java.util.List;

// import org.neo4j.codegen.asm.Label;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Record;



public class TestConnection {

	public static void main(String[] args) {
		
		Driver driver = GraphDatabase.driver(
//			  	"bolt://localhost:7687", AuthTokens.basic("neo4j", "1234"));
				"bolt://194.210.120.117:7687", AuthTokens.basic("neo4j", "Mafalda2021"));
		
		Session session = driver.session();
		
//		session.beginTransaction();
		
		System.out.println("Initiating the method");
		long startTime = System.currentTimeMillis();
		
		Result r1 = session.run("CREATE (baeldung:Company {name:\"Baeldung\"}) " +
				  "-[:owns]-> (tesla:Car {make: 'tesla', model: 'modelX'})" +
				  "RETURN baeldung, tesla");
		
		for (String k: r1.keys())
			System.out.print(k + " | ");
		System.out.println();
		
		List<Record> r2 = session.run("MATCH (c:Company) RETURN c.name").list();
		
		for (Record s: r2)
			System.out.println(s);
				
		session.close();
		driver.close();

			
			long endTime = System.currentTimeMillis();
			System.out.println("That took " + (endTime - startTime) + " milliseconds");

		}

	}




