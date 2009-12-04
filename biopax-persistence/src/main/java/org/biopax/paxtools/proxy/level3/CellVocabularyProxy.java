/*
 * CellVocabularyProxy.java
 *
 * 2007.12.03 Takeshi Yoneki
 * INOH project - http://www.inoh.org
 */

package org.biopax.paxtools.proxy.level3;

import org.biopax.paxtools.model.level3.*;
import org.hibernate.search.annotations.Indexed;

import javax.persistence.*;
import javax.persistence.Entity;

/**
 * Proxy for cellVocabulary
 */
@Entity(name="l3cellvocabulary")
@Indexed(index=BioPAXElementProxy.SEARCH_INDEX_NAME)
public class CellVocabularyProxy extends ControlledVocabularyProxy 
	implements CellVocabulary 
{
	public CellVocabularyProxy() {
	}

	@Transient
	public Class getModelInterface() {
		return CellVocabulary.class;
	}
}
