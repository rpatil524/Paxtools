package org.biopax.paxtools.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.*;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process; //separate import required here!
import org.biopax.paxtools.util.AnnotationMapKey;
import org.biopax.paxtools.util.Filter;
import org.biopax.paxtools.util.IllegalBioPAXArgumentException;

/**
 * An advanced BioPAX utility class that implements
 * several useful algorithms to extract root or child 
 * BioPAX elements, remove dangling, replace elements 
 * or identifiers, etc.
 * 
 * @author rodche
 *
 */
public class ModelUtils {
	private static final Log LOG = LogFactory.getLog(ModelUtils.class);
	
	/* 
	 * To ignore 'nextStep' property (in most algorithms),
	 * because it can eventually lead us outside current pathway,
	 * and normally step processes are listed in the pathwayComponent
	 * property as well.
	 */
	public static final Filter<PropertyEditor> nextStepFilter = new Filter<PropertyEditor>() {
		public boolean filter(PropertyEditor editor) {
			return !editor.getProperty().equals("nextStep")
				&& !editor.getProperty().equals("NEXT-STEP");
		}
	};
	
	public static final Filter<PropertyEditor> evidenceFilter = new Filter<PropertyEditor>() {
		public boolean filter(PropertyEditor editor) {
			return !editor.getProperty().equals("evidence")
				&& !editor.getProperty().equals("EVIDENCE");
		}
	};
	
	public static final Filter<PropertyEditor> pathwayOrderFilter = new Filter<PropertyEditor>() {
		public boolean filter(PropertyEditor editor) {
			return !editor.getProperty().equals("pathwayOrder"); 
		}
	};
	
	
	public static final MessageDigest MD5_DIGEST; //to calculate the PK from URI
	static {
		try {
			MD5_DIGEST = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Cannot instantiate MD5 MessageDigest!", e);
		}
	}
	
	
	private final Model model; // a model to hack ;)
	private final BioPAXIOHandler io;
	
	/**
	 * Controlled vocabulary terms for the RelationshipType 
	 * CV to be added with auto-generated/inferred comments
	 * and/or relationship xrefs.
	 * 
	 * We do not want to use "official" CV terms and IDs
	 * from the PSI-MI "cross-reference" branch for several reasons:
	 * - it's does not have terms we'd like and have those we'd not use
	 * - to distinguish auto-generated rel. xrefs from the original BioPAX data
	 * - hack: we also want put the related BioPAX element's URI into the xref's 'id' proerty
	 *   (this is not what the BioPAX standard officially regarding to use of xrefs)
	 * 
	 * @author rodche
	 *
	 */
	public static enum RelationshipType {
		PROCESS, // refers to a parent pathway or interaction
		ORGANISM,
		GENE, // term for, e.g., Entrez Gene rel. xrefs in protein references
		SEQUENCE, // e.g, to relate UniProt to RefSeq identifiers (incl. for splice variants...)
		; //TODO add more on use-case bases...
	}

	
	/**
	 * A comment (at least - prefix) to add to all generated objects
	 */
	public static final String COMMENT_FOR_GENERATED = "auto-generated";
	
	
	/* 
	 * URI prefix for auto-generated utility class objects
	 * (can be useful, for consistency, during, e.g.,
	 * data convertion, normalization, merge, etc.
	 */
	public static final String BIOPAX_URI_PREFIX = "urn:biopax:";

	
	/**
	 * This is to consistently create URI prefixes for  
	 * auto-generated/inferred Xref objects
	 * (except for PublicationXref, where creating of 
	 * something like, e.g., 'urn:miriam:pubmed:' is recommended). 
	 * 
	 * @param clazz
	 * @return
	 */
	public static String uriPrefixForGeneratedXref(Class<? extends Xref> clazz) {
		String prefix = BIOPAX_URI_PREFIX + clazz.getSimpleName() + ":";
		
		if(PublicationXref.class.equals(clazz) && LOG.isWarnEnabled()) {
			LOG.warn("uriPrefixForGeneratedXref: for a PublicationXref, " +
				"one should probably use a different prefix, " +
				"e.g., 'urn:miriam:pubmed:', etc. istead of this: "
				+ prefix);
		}
		
		return prefix;
	}
	
	
	/**
	 * Gets a URI for a special (internal, not standard, but useful) 
	 * RelationshipTypeVocabulary, which can be used for such objects
	 * auto-generated by this class methods (also by any BioPAX reasoner/tool)
	 * 
	 * @param relationshipType
	 * @return
	 */
	public static String relationshipTypeVocabularyUri(String relationshipType) {
		return BIOPAX_URI_PREFIX + "RelationshipTypeVocabulary:" 
			+ relationshipType.trim().toUpperCase();
	}
	
	

	/**
	 * Constructor.
	 * 
	 * @param model
	 */
	public ModelUtils(Model model) 
	{
		this.model = model;
		this.io = new SimpleIOHandler(model.getLevel());
		((SimpleIOHandler) this.io).mergeDuplicates(true);
		((SimpleIOHandler) this.io).normalizeNameSpaces(false);
	}
	
	
	/**
	 * Constructor.
	 * 
	 * @param level
	 */
	public ModelUtils(BioPAXLevel level) 
	{
		this.model = level.getDefaultFactory().createModel();
		this.io = new SimpleIOHandler(level);
		((SimpleIOHandler) this.io).mergeDuplicates(true);
		((SimpleIOHandler) this.io).normalizeNameSpaces(false);
	}
	
    /**
     * Replaces an existing BioPAX element with another one,
     * of the same or possibly equivalent type (or null),
     * recursively updates all the references to it 
     * (parents' object properties).
     * 
     * If you're actually replacing multiple objects in the same model,
     * for better performance, consider using {@link #replace(Map)} method
     * instead.
     * 
     * @param existing
     * @param replacement
     */
    public void replace(final BioPAXElement existing, final BioPAXElement replacement) 
    {
    	replace(Collections.singletonMap(existing, replacement));
    }

    
    /**
     * Replaces existing BioPAX elements with ones from the map,
     * and recursively updates all the object references
     * (parents' object properties) in a single pass.
     * At the end, it removes old and adds new elements to the model.
     * 
     * Even if current model is not self-consistent (is being currently modified, 
     * i.e., some objects there refer to external, implicit children, via 
     * BioPAX object properties or inverse properties), all BioPAX properties will be 
     * recursively updated anyway (and objects replaced), but some inverse properties 
     * may be or become dangling (still pointing to an external or replaced object); 
     * so, consider using {@link Model#repair()} before or after this method 
     * in such cases, as needed. Do not forget to write tests!
     * Also consider using {@link #removeObjectsIfDangling(Class)} after 
     * this method to also remove all dependents of the replaced objects.
     * 
     * @param subs
     */
    public void replace(final Map<? extends BioPAXElement, ? extends BioPAXElement> subs) 
    {    	
		Visitor visitor = new Visitor() {
			@Override
			public void visit(BioPAXElement domain, Object range, Model model,
					PropertyEditor editor) 
			{
				if(editor instanceof ObjectPropertyEditor && subs.containsKey(range)) 
				{
					ObjectPropertyEditor e = (ObjectPropertyEditor) editor;
					BioPAXElement replacement = subs.get(range);
					final String logMessage = 
						((BioPAXElement)range).getRDFId() + " (" +  range + "; " 
						+ ((BioPAXElement)range).getModelInterface().getSimpleName() + ")"
						+ " with " + ((replacement != null) ? replacement.getRDFId() : "") 
						+ " (" +  replacement + "); for property: " + e.getProperty()
						+ " of bean: " + domain.getRDFId() + " (" + domain + "; " 
						+ domain.getModelInterface().getSimpleName() + ")";
					
					if(replacement != null && !editor.getRange().isInstance(replacement))
						throw new IllegalBioPAXArgumentException("Incompatible type! " +
							" Attempted to replace " + logMessage);
					
					if (e.isMultipleCardinality()) {
						e.removeValueFromBean(range, domain);
					}
					e.setValueToBean(replacement, domain);
					
					if(LOG.isDebugEnabled())
						LOG.debug("Replaced " + logMessage);
				}
			}
		};
		
		Traverser traverser = new Traverser(SimpleEditorMap.get(model.getLevel()), visitor);
		for (BioPAXElement bpe: new HashSet<BioPAXElement>(model.getObjects())) {	
			traverser.traverse(bpe, model);
		}
		
		// remove/add in the model's registry (separate loops are required)
		for(BioPAXElement o : subs.keySet()) {
			model.remove(o);
		}
		for(BioPAXElement o : subs.values()) {
			if(o != null && !model.contains(o)) 
				model.add(o);
		}
    }
    
    /**
     * Deletes (recursively from the current model) 
     * only those child elements that would become "dangling" 
     * (not a property value of anything) if the parent 
     * element were (or already was) removed from the model.
     * 
     * @param parent
     * 
     * @deprecated use model.remove and more generic {@link #removeObjectsIfDangling(Class)} instead
     */
    @Deprecated
    public void removeDependentsIfDangling(BioPAXElement parent) 
    {	
		EditorMap em = SimpleEditorMap.get(model.getLevel());
    	// get the parent and all its children
		Fetcher fetcher = new Fetcher(em);
		Model childModel = model.getLevel().getDefaultFactory().createModel();
		fetcher.fetch(parent, childModel);
		
		// copy all elements
		Set<BioPAXElement> others = new HashSet<BioPAXElement>(model.getObjects());
		
		// retain only those not the parent nor its child
		// (asymmetric difference)
		others.removeAll(childModel.getObjects());
		
		// traverse from each of "others" to exclude those from "children" that are used
		for(BioPAXElement e : others) {
			final BioPAXElement bpe = e;
			// define a special 'visitor'
			AbstractTraverser traverser = new AbstractTraverser(em) 
			{
				@Override
				protected void visit(Object value, BioPAXElement parent, 
						Model m, PropertyEditor editor) 
				{
					if(value instanceof BioPAXElement 
							&& m.contains((BioPAXElement) value)) {
						m.remove((BioPAXElement) value); 
					}
				}
			};
			// check all biopax properties
			traverser.traverse(e, childModel);
		}
			
		// remove those left (would be dangling if parent were removed)!
		for (BioPAXElement o : childModel.getObjects()) {
			model.remove(o);
		}
    }

    /**
     * Gets the BioPAX model object that we analyze or modify.
     * 
     * @return
     */
	public Model getModel() {
		return model;
	}
	
	
	/**
	 * Finds a subset of "root" BioPAX objects of specific class (incl. sub-classes)
	 * 
	 * Note: however, such "root" elements may or may not be, a property of other
	 * elements, not included in the model.
	 * 
	 * @param filterClass 
	 * @return
	 */
	public <T extends BioPAXElement> Set<T> getRootElements(final Class<T> filterClass) 
	{
		// copy all such elements (initially, we think all are roots...)
		final Set<T> result = new HashSet<T>();
		result.addAll(model.getObjects(filterClass));
		
		// but we run from every element (all types)
		for(BioPAXElement e : model.getObjects()) {
			/* In order to visit all biopax properties of all elements 
			 * and to remove those reachable guys from the 'result',
			 * every time we create a fresh traverser, because, otherwise, 
			 * it would remember the state (stacks) from the last run, which 
			 * we would have to clear anyway;
			 */
			(new AbstractTraverser(SimpleEditorMap.get(model.getLevel())) 
			{
				@Override
				protected void visit(Object value, BioPAXElement parent, 
						Model model, PropertyEditor editor) {
					if(filterClass.isInstance(value)) 
						result.remove(value); 
				}
			}).traverse(e, null);
		}
		
		return result;
	}
	
	/**
	 * Iteratively removes dangling elements
	 * of given type, e.g., utility class,  
	 * from current model.
	 */
	public <T extends BioPAXElement> void removeObjectsIfDangling(Class<T> clazz) 
	{
		Set<T> dangling = getRootElements(clazz);
		// get rid of dangling objects
		if(!dangling.isEmpty()) {
			if(LOG.isInfoEnabled()) 
				LOG.info(dangling.size() + " BioPAX utility objects " +
						"were/became dangling, and they "
						+ " will be deleted...");
			

			for(BioPAXElement thing : dangling) {
				model.remove(thing);
				if(LOG.isDebugEnabled())
					LOG.debug("removed (dangling) " 
						+ thing.getRDFId() + " (" 
						+ thing.getModelInterface().getSimpleName()
						+ ") " + thing);
			}
			
			// some may have become dangling now, so check again...
			removeObjectsIfDangling(clazz);
		}
	}
	

	/**
	 * For the current (internal) model, this method 
	 * iteratively copies given property values 
	 * from parent BioPAX elements to children.
	 * If the property is multiple cardinality property, it will add
	 * new values, otherwise - it will set it only if was empty; 
	 * in both cases it won't delete/override existing values!
	 * 
	 * @see PropertyReasoner
	 * 
	 * @param property property name
	 * @param forClasses (optional) infer/set the property for these types only
	 */
	public void inferPropertyFromParent(final String property, 
			final Class<? extends BioPAXElement>... forClasses) 
	{		
		// for each ROOT element (puts a strict top-down order on the following)
		Set<BioPAXElement> roots = getRootElements(BioPAXElement.class);
		for(BioPAXElement bpe : roots) {
			PropertyReasoner reasoner = new PropertyReasoner(property, 
					SimpleEditorMap.get(model.getLevel()));
			reasoner.setDomains(forClasses);
			reasoner.inferPropertyValue(bpe);
		}
	}
		
	/**
	 * Cuts the BioPAX model off other models and BioPAX objects 
	 * by essentially performing write/read to/from OWL. 
	 * The resulting model contains new objects with same IDs 
	 * and have object properties "fixed", i.e., dangling values 
	 * become null/empty, and inverse properties (e.g. xrefOf)
	 * re-calculated. The original model is unchanged.
	 * 
	 * @return copy of the model
	 * @throws IOException 
	 */
	public Model writeRead()
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		io.convertToOWL(model, baos);
		return io.convertFromOWL(new ByteArrayInputStream(baos.toByteArray()));
	}	

	
	/**
	 * Gets direct children of a given BioPAX element
	 * and adds them to a new model.
	 * 
	 * @param bpe
	 * @return
	 */
	public Model getDirectChildren(BioPAXElement bpe) 
	{	
		Model model = this.model.getLevel().getDefaultFactory().createModel();
		
		AbstractTraverser traverser = new AbstractTraverser(
				SimpleEditorMap.get(model.getLevel())) {
			@Override
			protected void visit(Object range, BioPAXElement domain,
					Model model, PropertyEditor editor) {
				if (range instanceof BioPAXElement 
						&& !model.contains((BioPAXElement) range)) {
					model.add((BioPAXElement) range);
				}
			}
		};
		
		traverser.traverse(bpe, model);
		
		return model;
	}
	
	
	/**
	 * Gets all the child BioPAX elements of a given BioPAX element
	 * (using the "tuned" {@link Fetcher}) and adds them to a 
	 * new model.
	 * 
	 * @param bpe
	 * @param filters property filters (e.g., for Fetcher to skip some properties). Default is to skip 'nextStep'.
	 * @return
	 */
	public Model getAllChildren(BioPAXElement bpe,
			Filter<PropertyEditor>... filters) {
		Model model = this.model.getLevel().getDefaultFactory().createModel();
		EditorMap editorMap = SimpleEditorMap.get(model.getLevel());
		if (filters.length == 0) {
			new Fetcher(editorMap, nextStepFilter).fetch(bpe, model);
		} else {
			new Fetcher(editorMap, filters).fetch(bpe, model);
		}
		model.remove(bpe); // remove the parent

		return model;
	}	

	
	/**
	 * Collects all child BioPAX elements of a given BioPAX element
	 * (using the "tuned" {@link Fetcher})
	 * 
	 * @param bpe
	 * @param filters property filters (e.g., for Fetcher to skip some properties). Default is to skip 'nextStep'.
	 * @return
	 */
	public Set<BioPAXElement> getAllChildrenAsSet(BioPAXElement bpe,
			Filter<PropertyEditor>... filters) {
		Set<BioPAXElement> toReturn = null;
		EditorMap editorMap = SimpleEditorMap.get(model.getLevel());
		if (filters.length == 0) {
			toReturn = new Fetcher(editorMap, nextStepFilter).fetch(bpe);
		} else {
			toReturn = new Fetcher(editorMap, filters).fetch(bpe);
		}

		toReturn.remove(bpe); // remove the parent
		
		return toReturn;
	}
	
	
	/**
	 * Collects direct children of a given BioPAX element
	 * 
	 * @param bpe
	 * @return
	 */
	public Set<BioPAXElement> getDirectChildrenAsSet(BioPAXElement bpe) 
	{	
		final Set<BioPAXElement> toReturn = new HashSet<BioPAXElement>();
		
		AbstractTraverser traverser = new AbstractTraverser(
				SimpleEditorMap.get(model.getLevel())) {
			@Override
			protected void visit(Object range, BioPAXElement domain,
					Model model, PropertyEditor editor) {
				if (range instanceof BioPAXElement) {
					toReturn.add((BioPAXElement) range);
				}
			}
		};
		
		traverser.traverse(bpe, null);
		return toReturn;
	}
	
	
	/**
	 * Creates "process" (membership) relationship xrefs 
	 * for each child {@link Entity} if possible. 
	 * This is Level3 specific.
	 * 
	 * For each child {@link Entity} of every process 
	 * (of the type given by the second argument), creates a 
	 * relationship xref with the following properties:
	 * - db = provider (a name given by the second parameter)
	 * - id = the rdfId (URI) of the parent process
	 * - relationshipType = controlled vocabulary: "process" (MI:0359), urn:miriam:obo.mi:MI%3A0359
	 * - comment = "Auto-generated by Paxtools" (also added to the CV and its unification xref)
	 * 
	 * @param <T>
	 * @param processClass to relate entities with an interaction/pathway of this type 
	 * @deprecated still ok for Pathway, but it is confusing, expensive and may be completely wrong to use with Interaction types
	 */
	@Deprecated
	public <T extends Process> void generateEntityProcessXrefs(
			Class<T> processClass) 
	{	
		// use a special relationship CV;
		RelationshipTypeVocabulary cv = getTheRelatioshipTypeCV(RelationshipType.PROCESS);
		
		Set<T> processes = new HashSet<T>(model.getObjects(processClass)); //to avoid concurr. mod. ex.
		for(T ownerProc : processes) 
		{
			// prepare the xref to use in children
			String relXrefId = generateURIForXref(COMMENT_FOR_GENERATED, ownerProc.getRDFId(), RelationshipXref.class);
			RelationshipXref rx =  (RelationshipXref) model.getByID(relXrefId);
			if (rx == null) {
				rx = model.addNew(RelationshipXref.class, relXrefId);
				rx.addComment(COMMENT_FOR_GENERATED);
				rx.setDb(COMMENT_FOR_GENERATED);
				rx.setId(ownerProc.getRDFId());
				rx.setRelationshipType(cv);
			}
			
			// add the xref to all (biologically) child entities
			Model childModel = getAllChildren(ownerProc, pathwayOrderFilter, evidenceFilter);
			saveRelationship(childModel.getObjects(Entity.class), rx, true, false);
		}
	}

	
	/**
	 * Creates pathway membership relationship xrefs and
	 * annotations for each child element if possible. 
	 * This is Level3 specific. 
	 * @see {@link BioPAXElement#getAnnotations()}
	 * 
	 * For each child {@link XReferrable} of every pathway it creates a 
	 * relationship xref with the following properties:
	 * - db = provider (a name given by the second parameter)
	 * - id = the rdfId (URI) of the parent process
	 * - relationshipType = controlled vocabulary: "process" (MI:0359), urn:miriam:obo.mi:MI%3A0359
	 * - comment = "Auto-generated by Paxtools" (also added to the CV and its unification xref)
	 * And also adds parent pathways to child elements's annotation map 
	 * using {@link AnnotationMapKey} "PARENT_PATHWAYS" as the key.
	 * 
	 *  @param forBiopaxType
	 *  @param addRelationshipXrefs
	 *  @param addPathwaysToAnnotations
	 *  
	 */
	public void calculatePathwayMembership(Class<? extends BioPAXElement> forBiopaxType, 
			boolean addRelationshipXrefs, boolean addPathwaysToAnnotations) 
	{	
		Set<Pathway> processes = new HashSet<Pathway>(model.getObjects(Pathway.class)); //to avoid concurr. mod. ex.
		for(Pathway ownerProc : processes) 
		{
			// use a special relationship CV;
			RelationshipTypeVocabulary cv = getTheRelatioshipTypeCV(RelationshipType.PROCESS);
			
			// prepare the xref to use in children
			String relXrefId = generateURIForXref(COMMENT_FOR_GENERATED, ownerProc.getRDFId(), RelationshipXref.class);
			RelationshipXref rx =  (RelationshipXref) model.getByID(relXrefId);
			if (rx == null) {
				rx = model.addNew(RelationshipXref.class, relXrefId);
				rx.addComment(COMMENT_FOR_GENERATED);
				rx.setDb(COMMENT_FOR_GENERATED);
				rx.setId(ownerProc.getRDFId());
				rx.setRelationshipType(cv);
			}
			
			// add the xref to all (biologically) child entities
			Model childModel = getAllChildren(ownerProc, pathwayOrderFilter, evidenceFilter);
			saveRelationship(childModel.getObjects(forBiopaxType), rx, addRelationshipXrefs, addPathwaysToAnnotations);			
		}
	}
	
	
	/**
	 * Adds the relationship xref to every xreferrable entity in the set
	 * (and optionally - the corresponding pathway to the annotations map) 
	 * 
	 * @param objects
	 * @param rx
	 * @param addRelationshipXrefs
	 * @param addPathwaysToAnnotations
	 */
	private void saveRelationship(Set<? extends BioPAXElement> elements, RelationshipXref rx, 
			boolean addRelationshipXrefs, boolean addPathwaysToAnnotations) 
	{
		if (addPathwaysToAnnotations || addRelationshipXrefs) {
			for (BioPAXElement ent : elements) {
				if (addRelationshipXrefs && ent instanceof XReferrable)
					((XReferrable) ent).addXref(rx);

				if (addPathwaysToAnnotations) {
					final String key = AnnotationMapKey.PARENT_PATHWAYS
							.toString();
					Set<Pathway> ppw = (Set<Pathway>) ent.getAnnotations().get(
							key);
					if (ppw == null) {
						ppw = new HashSet<Pathway>();
						ent.getAnnotations().put(key, ppw);
					}
					ppw.add((Pathway) model.getByID(rx.getId()));
				}
			}
		} else {
			throw new IllegalArgumentException("Useless call or a bug: " +
					"both boolean parameters are 'false'!");
		}
	}
	
	/**
	 * Auto-generates organism relationship xrefs - 
	 * for BioPAX entities that do not have such property (but their children do), 
	 * such as of Interaction, Protein, Complex, DNA, Protein, etc. classes.
	 * 
	 * Infers organisms in two steps:
	 * 
	 * 1. add organisms as relationship xrefs 
	 *    of {@link SimplePhysicalEntity} objects (from EntityReference objects), 
	 *    except for smallmolecules.
	 * 2. infer organism information recursively via all children, 
	 *    but only when children are also Entity objects (not utility classes)
	 *    (equivalently, this can be achieved by collecting all the children first, 
	 *     though not visiting properties who's range is a sub-class of UtilityClass)
	 * 
	 */
	public void generateEntityOrganismXrefs() 
	{
		// The first pass (physical entities)
		Set<SimplePhysicalEntity> simplePEs = // prevents concurrent mod. exceptions
			new HashSet<SimplePhysicalEntity>(model.getObjects(SimplePhysicalEntity.class));
		for(SimplePhysicalEntity spe : simplePEs) {
			//get the organism value (BioSource) from the ER; create/add rel. xrefs to the spe
			EntityReference er = spe.getEntityReference();
			// er can be generic (member ers), so -
			Set<BioSource> organisms = getOrganismsFromER(er);
			addOrganismXrefs(spe, organisms);
		}
		
		// use a special filter for the (child elements) fetcher
		Filter<PropertyEditor> entityRangeFilter = new Filter<PropertyEditor>() {
			@Override
			public boolean filter(PropertyEditor editor) {
				// values are of Entity sub-class -
				return Entity.class.isAssignableFrom(editor.getRange());
			}
		};
		
		/* 
		 * The second pass (all entities, particularly - 
		 * Pathway, Gene, Interaction, Complex and generic physical entities
		 */
		Set<Entity> entities = new HashSet<Entity>(model.getObjects(Entity.class));
		for(Entity entity : entities) {
			Set<BioSource> organisms = new HashSet<BioSource>();
			
			//If the entity has "true" organism property (it's Gene or Pathway), collect it
			addOrganism(entity, organisms);
		
			/* collect its children (- of Entity class only, 
			 * i.e., won't traverse into UtilityClass elements's properties)
			 * 
			 * Note: although Stoichiometry.physicalEntity, 
			 *       ExperimentalForm.experimentalFormEntity, 
			 *       and PathwayStep.stepProcess are (only) examples 
			 *       when an Entity can be value of a UtilityClass
			 *       object's property, we have to skip these 
			 *       utility classes (with their properties) anyway.
			 */
			Model submodel = getAllChildren(entity, entityRangeFilter);
			// now, collect organism values from the children entities 
			// (using both property 'organism' and rel.xrefs created above!)
			for(Entity e : submodel.getObjects(Entity.class)) {
				//skip SM
				if(e instanceof SmallMolecule)
					continue;
				
				//has "true" organism property? (a Gene or Pathway?) Collect it.
				addOrganism(e, organisms);
				// check in rel. xrefs
				for(Xref x : e.getXref()) 
				{
					if(x instanceof RelationshipXref) {	
						if(isOrganismRelationshipXref((RelationshipXref) x))  {
							//previously, xref.id was set to a BioSource' ID!
							assert(x.getId() != null);
							BioSource bs = (BioSource) model.getByID(x.getId());
							assert(bs != null);
							organisms.add(bs);
						}
					}
				}
			}
			
			// add all the organisms (xrefs) to this entity
			addOrganismXrefs(entity, organisms);
		}
	}

	
	/**
	 * Returns a set of organism of the entity reference.
	 * If it is a generic entity reference (has members), 
	 * - will recursively collect all the organisms from 
	 * its members.  
	 * 
	 * @param er
	 * @return
	 */
	private Set<BioSource> getOrganismsFromER(EntityReference er) {
		Set<BioSource> organisms = new HashSet<BioSource>();
		if(er instanceof SequenceEntityReference) {
			BioSource organism = ((SequenceEntityReference) er).getOrganism();
			if(organism != null) {
				organisms.add(organism);
			}
			
			if(!er.getMemberEntityReference().isEmpty()) {
				for(EntityReference mer : er.getMemberEntityReference()) {
					organisms.addAll(
							getOrganismsFromER(mer)
					);
				}
			}
		}
		return organisms;
	}

	
	/**
	 * Adds entity's organism value (if applicable and it has any) to the set.
	 * 
	 * @param entity
	 * @param organisms
	 */
	private void addOrganism(BioPAXElement entity, Set<BioSource> organisms) {
		PropertyEditor editor = SimpleEditorMap.get(model.getLevel())
				.getEditorForProperty("organism", entity.getModelInterface());
		if (editor != null) {
			Object o = editor.getValueFromBean(entity);
			if(o != null) {
				Set<BioSource> seto = (Set<BioSource>) o;
				organisms.addAll(seto);
			}
		}
	}

	
	/**
	 * Generates a relationship xref for each 
	 * organism (BioSource) in the list and adds them to the entity.
	 * 
	 * @param entity
	 * @param organisms
	 */
	private void addOrganismXrefs(Entity entity, Set<BioSource> organisms) {
		// create/find a RelationshipTypeVocabulary with term="ORGANISM"
		RelationshipTypeVocabulary cv = getTheRelatioshipTypeCV(RelationshipType.ORGANISM);
		// add xref(s) to the entity
		for(BioSource organism : organisms) {
			String db = COMMENT_FOR_GENERATED; 
			String id = organism.getRDFId();
			String relXrefId = generateURIForXref(db, id, RelationshipXref.class);
			RelationshipXref rx =  (RelationshipXref) model.getByID(relXrefId);
			if (rx == null) {
				rx = model.addNew(RelationshipXref.class, relXrefId);
				rx.setRelationshipType(cv);
				rx.addComment(COMMENT_FOR_GENERATED);
				rx.setDb(db);
				rx.setId(id);
			}
			entity.addXref(rx);
		}
	}

	
	/**
	 * Finds in the model or creates a new special RelationshipTypeVocabulary 
	 * controlled vocabulary with the term value defined by the argument (enum).
	 * 
	 * @param relationshipType
	 * @return
	 */
	private RelationshipTypeVocabulary getTheRelatioshipTypeCV(RelationshipType relationshipType) 
	{
		String cvId = relationshipTypeVocabularyUri(relationshipType.name());
		// try to get from the model first
		RelationshipTypeVocabulary cv = (RelationshipTypeVocabulary) model.getByID(cvId);
		if (cv == null) { // one instance per model to be created
			cv = model.addNew(RelationshipTypeVocabulary.class, cvId);
			cv.addTerm(relationshipType.name());
			cv.addComment(COMMENT_FOR_GENERATED);
			
			/* disabled: in favor of custom terms from RelationshipType -
			//String uxid = "urn:biopax:UnificationXref:MI_MI%3A0359";
			String uxid = generateURIForXref("MI", "MI:0359", UnificationXref.class);
			UnificationXref ux = (UnificationXref) model.getByID(uxid);
			if(ux == null) {
				ux = model.addNew(UnificationXref.class, uxid);
				ux.addComment(COMMENT_FOR_GENERATED);
				ux.setDb("MI");
				ux.setId("MI:0359");
			}
			cv.addXref(ux);
			*/
		}
		
		return cv;
	}
	
	
	/**
	 * Builds a "normalized" RelationshipXref URI.
	 * 
	 * @param db
	 * @param id
	 * @param type TODO
	 * @return new ID (URI); not null (unless it's a bug :))
	 * 
	 */
	public static String generateURIForXref(String db, String id, Class<? extends Xref> type) 
	{
		String rdfid;
		String prefix = uriPrefixForGeneratedXref(type); 
			
		// add the local part of the URI encoded -
		try {
			rdfid = prefix + URLEncoder
				.encode(db.trim() + "_" + id.trim(), "UTF-8")
					.toUpperCase();
		} catch (UnsupportedEncodingException e) {
			if(LOG.isWarnEnabled())
				LOG.warn("ID UTF-8 encoding failed! " +
					"Using the platform default (deprecated method).", e);
			rdfid = prefix + URLEncoder
				.encode(db.trim() + "_" + id.trim()).toUpperCase();
		}

		return rdfid;
	}

	public Map<Class<? extends BioPAXElement>, Integer> generateClassMetrics()
	{
		Map<Class<? extends BioPAXElement>, Integer> metrics =
				new HashMap<Class<? extends BioPAXElement>, Integer>();
		for (BioPAXElement bpe : this.model.getObjects())
		{
			Integer count = metrics.get(bpe.getModelInterface());
			if(count == null)
			{
				count = 1;
			}
			else
			{
				count = count+1;
			}
			metrics.put(bpe.getModelInterface(),count);
		}
		return metrics;
	}
	
	
	public <T extends BioPAXElement> T getObject(String urn, Class<T> clazz) 
	{
		BioPAXElement bpe = model.getByID(urn);
		if(clazz.isInstance(bpe)) {
			return (T) bpe;
		} else {
			return null;
		}
	}

	/**
	 * This is a special (not always applicable) utility method.
	 * It finds the list of IDs of objects in the model
	 * that have a NORMALIZED xref equivalent (same db,id,type) 
	 * to at least one of the specified xrefs.
	 * 
	 * @param xrefs
	 * @param clazz
	 * @return
	 */
	public Set<String> getByXref(Set<? extends Xref> xrefs, 
			Class<? extends XReferrable> clazz) 
	{
			Set<String> toReturn = new HashSet<String>();
			
			for (Xref xref : xrefs) {			
				// map to a normalized RDFId for this type of xref:
				if(xref.getDb() == null || xref.getId() == null) {
					continue;
				}
				
				String xurn = generateURIForXref(xref.getDb(), 
					xref.getId(), (Class<? extends Xref>) xref.getModelInterface());
				
				Xref x = (Xref) model.getByID(xurn);
				if (x != null) {
					// collect owners's ids (of requested type only)
					for (XReferrable xr : x.getXrefOf()) {
						if (clazz.isInstance(xr)) {
							toReturn.add(xr.getRDFId());
						}
					}
				} 
			}
			
			return toReturn;
	}

	
	/**
	 * 
	 * 
	 * @param rx
	 * @return
	 */
	public static boolean isOrganismRelationshipXref(RelationshipXref rx) {
		RelationshipTypeVocabulary cv = rx.getRelationshipType();
		return cv != null && cv.getRDFId().equalsIgnoreCase(
			relationshipTypeVocabularyUri(RelationshipType.ORGANISM.name()));
	}

	
	/**
	 * 
	 * @param rx
	 * @return
	 */
	public static boolean isProcessRelationshipXref(RelationshipXref rx) {
		RelationshipTypeVocabulary cv = rx.getRelationshipType();
		return cv != null && cv.getRDFId().equalsIgnoreCase(
			relationshipTypeVocabularyUri(RelationshipType.PROCESS.name()));
	}


	/**
	 * Calculates MD5 hash code (as 32-byte hex. string).
	 * 
	 * @param id
	 * @return
	 */
	public static String md5hex(String id) {
		byte[] digest = MD5_DIGEST.digest(id.getBytes());
		StringBuffer sb = new StringBuffer();
		for (byte b : digest) {
			sb.append(Integer.toHexString((int) (b & 0xff) | 0x100).substring(1, 3));
		}
		String hex = sb.toString();
        return hex;
	}
}
