package org.biopax.paxtools.scripting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.junit.Ignore;
import org.junit.Test;
import org.biopax.paxtools.io.simpleIO.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Iterator;


public class ShellTest {
    @Test
    public void testLoad() throws Exception
    {
        TestModelIterator tmi = new TestModelIterator(BioPAXLevel.L3, ".");
        while (tmi.hasNext()) {
            Model next = tmi.next();
        }
    }


    class TestModelIterator implements Iterator<Model> {
        private Log log = LogFactory.getLog(TestModelIterator.class);
        private Iterator<String> fileNames;
        private String dir;
        private SimpleReader simpleReader;

        public TestModelIterator(BioPAXLevel level, String pathRoot) throws URISyntaxException {
            this(level,
                    pathRoot,
                    new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return (name.endsWith(".owl"));
                        }
                    }
            );

        }


        public TestModelIterator(BioPAXLevel level, String pathRoot, FilenameFilter filter)
                throws URISyntaxException {
            dir = pathRoot + File.separator + level.name();

            System.out.println("dir = " + dir);

            File testDir = new File(getClass()
                    .getClassLoader().getResource(dir).toURI());

            System.out.println("testDir) = " + testDir);
            System.out.println(Arrays.toString(testDir.listFiles()));

            fileNames = Arrays.asList(testDir.list(filter)).iterator();

            simpleReader = new SimpleReader(level);
        }

        public boolean hasNext() {
            return fileNames.hasNext();
        }

        public Model next() {
            String s = fileNames.next();
            System.out.println("Reading " + s);
            InputStream in = getClass().getClassLoader() // - at the root classpath
                    .getResourceAsStream(dir + File.separator + s);
            return simpleReader.convertFromOWL(in);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}