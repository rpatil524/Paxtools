package org.biopax.paxtools.normalizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Singleton bio identifier resolver utility.
 */
public class Resolver {
  private static final Logger LOG = LoggerFactory.getLogger(Resolver.class);
  private static final Map<String, Namespace> namespaces;
  private static Map<String,String> spellmap; // compressed name -> prefix (combines custom spellmap.json and registry)
  private static Map<String,String> synonymap;// synonym -> prefix (build from custom synonymap.json and registry)

  public static final String BIOREGISTRY_IO = "bioregistry.io/";

  protected Resolver() {
  }

  static {
    InputStream is = null;
    try {
      ObjectMapper om = new ObjectMapper();
      om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); //to quietly ignore the data we don't need
//			om.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));
      // load a map of "compressed" (non letter/digit chars were removed) spelling variants to combine with namespaces map
      is = Resolver.class.getResourceAsStream("spellmap.json");
      spellmap = new ConcurrentSkipListMap<>(om.readValue(is, new TypeReference<Map<String,String>>(){}));
      // load a custom map of synonyms
      is = Resolver.class.getResourceAsStream("synonymap.json");
      synonymap = new ConcurrentSkipListMap<>(om.readValue(is, new TypeReference<Map<String,String>>(){}));
      // load registry.json
      is = Resolver.class.getResourceAsStream("registry.json");
      namespaces = new ConcurrentSkipListMap<>(om.readValue(is, new TypeReference<Map<String,Namespace>>(){}));
      LOG.info("read registry.json to the namespaces map; size: " + namespaces.size());

      //Post-process registry entries to add synonyms and spellings
      namespaces.forEach((prefix, ns) -> {
        ns.setPrefix(prefix);
        //todo: skip for deprecated?
        String uname = ns.getName().toUpperCase();
        String upref = prefix.toUpperCase();
        //add name, prefix, synonyms to the synonymap (uppercase)
        //also - to spellmap (removing non-alphanumeric chars)
        synonymap.put(uname, prefix);
        synonymap.put(upref, prefix);
        spellmap.put(uname.replaceAll("[^A-Z0-9]",""), prefix);
        spellmap.put(upref.replaceAll("[^A-Z0-9]",""), prefix);
        //add name, prefix, synonyms to synonymap
        ns.getSynonyms().forEach(syn -> {
          String s = syn.toUpperCase();
          synonymap.putIfAbsent(s, prefix);
          spellmap.putIfAbsent(s.replaceAll("[^A-Z0-9]",""), prefix);
        });
      });
      LOG.info("initialized");
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if(is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
  }


  /**
   * Checks if the identifier given follows the regular expression
   * of its data type (also provided).
   *
   * @param identifier internal identifier used by the data type
   * @param datatype   name, synonym or URI of a data type
   * @return "true" if the identifier follows the regular expression, "false" otherwise
   * @throws IllegalArgumentException when datatype not found
   */
  public static boolean checkRegExp(String identifier, String datatype) {
    Namespace dt = getNamespace(datatype);
    if(dt == null) {
      return false;
    }

    //remove "banana" and "peel" prefix from the identifier if any defined and present
    String banana = dt.getBanana();
    if(StringUtils.isNotBlank(banana)) {
      identifier = identifier.replaceFirst(banana+dt.getBanana_peel(), "");
    }

    return Pattern.compile(dt.getPattern()).matcher(identifier).find();
  }


  /**
   * Gets Namespace by its Name, Prefix, URI (URN/URL), known synonym or misspelling.
   *
   * @param key a datatype name, prefix or URN/URL (case insensitive)
   * @return Namespace bean or null
   */
  public static Namespace getNamespace(String key) {
    return getNamespace(key, true);
  }


  /**
   * Gets Namespace by its Name, Prefix, URN/URL, or (optionally) spelling variant or synonym
   *
   * @param key a name (case insensitive)
   * @param allowVariants to allow or not searching with some known synonyms/misspellings
   * @return Namespace bean or null
   */
  public static Namespace getNamespace(String key, boolean allowVariants) {
    if(StringUtils.isBlank(key)) {
      return null;
    }

    //uppercase!
    key = key.toUpperCase();
    //quick-fix some obsolete prefixes
    key = StringUtils.removeEnd(key, "/");
    key = key.replaceFirst("OBO\\.", "");
    key = key.replaceFirst("PSI-", "");
    key = key.replaceFirst("URN:MIRIAM:", "");
    key = key.replaceFirst(".*IDENTIFIERS\\.ORG/(.*/)?", ""); //e.g. match both ".ORG/CHEBI/CHEBI:1234" and ".ORG/CHEBI:1234" variants
    key = key.replaceFirst(".*BIOREGISTRY\\.IO/", "");

    String prefix = key.toLowerCase();
    Namespace ns = namespaces.get(prefix);
    //also try synonyms
    if(ns == null) {
      prefix = synonymap.get(key); //uppercase search
      if(prefix != null)
        ns = namespaces.get(prefix.toLowerCase());
    }

    if(ns == null && allowVariants) {
      //keep only alphanumeric chars in the key (it's uppercase) and search the spellmap
      prefix = spellmap.get(key.replaceAll("[^A-Z0-9]",""));
      if(prefix != null) {
        ns = namespaces.get(prefix.toLowerCase());
      }
    }

    return ns;
  }

  /**
   * Builds a URI of the bioentity (e.g., "bioregistry.io/go:0045202")
   * from the collection name/synonym and bio id.
   *
   * @param name - name, URI, or ID of a data collection (examples: "ChEBI", "go")
   * @param id   identifier of an entity within the data type (examples: "GO:0045202" or "0045202", "P62158")
   * @return URI (without the protocol prefix)
   */
  public static String getURI(String name, String id) {
    String cid = getCURIE(name, id);
    return cid != null ? BIOREGISTRY_IO + cid : null;
  }

  /**
   * Builds a normalized CURIE (Compact URI/ID) of the biochemical entity
   * from the collection name/synonym and bio id.
   * @param name bio namespace/db/collection name
   * @param id bio identifier
   * @return
   */
  public static String getCURIE(String name, String id) {
    Namespace ns = getNamespace(name, true);
    if (ns != null) {
      if (checkRegExp(id, name)) {
        String prefix = ns.getPrefix();
        //remove "banana" and "peel" prefix from the identifier if any defined and present
        String banana = ns.getBanana();
        if(StringUtils.isNotBlank(banana)) {
          id = id.replaceFirst(banana + ns.getBanana_peel(), "");
        }
        return prefix + ":" + id;
      }
    }
    return null;
  }

  /**
   * Gets the unmodifiable map of the bio identifier types registry records.
   * @return
   */
  public static Map<String, Namespace> getNamespaces() {
    return Collections.unmodifiableMap(namespaces);
  }

  /**
   * Gets the unmodifiable map - mapping an identifier type name/variant/synonym
   * (non-alphanumeric chars are removed) to the corresponding record/namespace "prefix".
   * All the key/values are upper case.
   * @return
   */
  public static Map<String, String> getSpellmap() {
    return Collections.unmodifiableMap(spellmap);
  }

  /**
   * Customize the mapping from a bio identifier type name/variant/synonym
   * (non-alphanumeric chars should be removed) to the corresponding record/namespace "prefix".
   * All the key/values should be stored in upper case.
   * @return
   */
  public static void setSpellmap(Map<String, String> map) {
    spellmap = map;
  }

  /**
   *
   * @param name
   * @return
   */
  public static boolean isKnownNameOrVariant(String name) {
    return name != null && (
            namespaces.containsKey(name.toLowerCase())
            || synonymap.containsKey(name.toUpperCase())
            || spellmap.containsKey(name.toUpperCase().replaceAll("[^a-zA-Z0-9]",""))
    );
  }

}
