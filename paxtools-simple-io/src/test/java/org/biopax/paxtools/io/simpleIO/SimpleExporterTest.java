package org.biopax.paxtools.io.simpleIO;
/**
 * Created by IntelliJ IDEA.
 * User: Emek
 * Date: Feb 25, 2008
 * Time: 12:11:27 PM
 */

import junit.framework.TestCase;

import org.biopax.paxtools.impl.level3.Level3FactoryImpl;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Level3Factory;
import org.biopax.paxtools.model.level3.Protein;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class SimpleExporterTest extends TestCase
{

    @Test
    public void testExportL2() throws InvocationTargetException, IOException,
                                      IllegalAccessException
    {
    	SimpleExporter simpleExporter = new SimpleExporter(BioPAXLevel.L2);
        Model model = BioPAXLevel.L2.getDefaultFactory().createModel();
        FileOutputStream out = new FileOutputStream(
        		getClass().getResource("").getFile() 
        		+ File.separator + "simple.owl"
        	);
        simpleExporter.convertToOWL(model, out);
        out.close();

    }
    
	@Test
	public void testReadWriteL2()
	{
		String s = "L2" + File.separator 
			+ "biopax_id_557861_mTor_signaling.owl";
		SimpleReader simpleReader = new SimpleReader();
		
		System.out.println("file = " + s);
		    try
		    {
			    System.out.println("starting "+s);
			    InputStream in = getClass().getClassLoader().getResourceAsStream(s);
			    assertNotNull(in);
				Model model =   simpleReader.convertFromOWL(in);
				assertNotNull(model);
				assertFalse(model.getObjects().isEmpty());
			    System.out.println("Model has "+model.getObjects().size()+" objects)");
			    FileOutputStream out =
	                new FileOutputStream(
	                	getClass().getResource("").getFile() 
	                	+ File.separator + "simpleReadWrite.owl"
	                	);
				SimpleExporter simpleExporter = new SimpleExporter(BioPAXLevel.L2);
				simpleExporter.convertToOWL(model, out);
				out.close();
		    }
		    catch (Exception e)
		    {
		        e.printStackTrace();
		        System.exit(1);
		    }
	}
	
	@Test
	public void testReadWriteL3()
	{
		String s = "L3" + File.separator + "biopax3-short-metabolic-pathway.owl";
		SimpleReader simpleReader = new SimpleReader(BioPAXLevel.L3);
		
		System.out.println("file = " + s);
		    try
		    {
			    System.out.println("starting "+s);
			    InputStream in =  getClass().getClassLoader().getResourceAsStream(s);
				Model model =   simpleReader.convertFromOWL(in);
				assertNotNull(model);
				assertFalse(model.getObjects().isEmpty());
			    System.out.println("Model has "+model.getObjects().size()+" objects)");
			    FileOutputStream out =
	                new FileOutputStream(
	                	getClass().getResource("").getFile() 
	                	+ File.separator + "simpleReadWrite.owl"
	                	);
				SimpleExporter simpleExporter = new SimpleExporter(BioPAXLevel.L3);
				simpleExporter.convertToOWL(model, out);
				out.close();
		    }
		    catch (Exception e)
		    {
		        e.printStackTrace();
		        System.exit(1);
		    }
	}
	
    @Test
    public void testDuplicateNamesByExporter() throws IOException {
    	Level3Factory level3 = new Level3FactoryImpl();
    	Protein p = level3.createProtein();
    	String name = "aDisplayName";
    	p.setRDFId("myProtein");
    	p.setDisplayName(name);
    	p.addComment("Display Name should not be repeated again in the Name property!");
    	Model m = level3.createModel();
    	m.add(p);
    	
	    FileOutputStream out =
            new FileOutputStream( // to the target test dir
            	getClass().getResource("").getFile() 
            		+ File.separator + "testDuplicateNamesByExporter.xml"
            	);
		SimpleExporter simpleExporter = new SimpleExporter(BioPAXLevel.L3);
		simpleExporter.convertToOWL(m, out);
		out.close();
    	
		// read
    	BufferedReader in = new BufferedReader(
    		new FileReader(getClass().getResource("").getFile() 
            		+ File.separator + "testDuplicateNamesByExporter.xml"));
    	char[] buf = new char[1000];
    	in.read(buf);
    	String xml = new String(buf);
    	if(xml.indexOf(name) != xml.lastIndexOf(name)) {
    		fail("displayName gets duplicated by the SimpleExporter!");
    	}
    	
    }
}