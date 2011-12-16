package org.biopax.paxtools.impl.level3;

import org.biopax.paxtools.model.level3.Transport;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.search.annotations.Indexed;

import javax.persistence.Entity;
import javax.persistence.Transient;

@Entity
@Indexed//(index=BioPAXElementImpl.SEARCH_INDEX_NAME)
@org.hibernate.annotations.Entity(dynamicUpdate = true, dynamicInsert = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class TransportImpl extends ConversionImpl implements Transport
{
	public TransportImpl() {
	}
	
	// --------------------- Interface BioPAXElement ---------------------

	@Transient
    public Class<? extends Transport> getModelInterface()
	{
		return Transport.class;
	}
}
