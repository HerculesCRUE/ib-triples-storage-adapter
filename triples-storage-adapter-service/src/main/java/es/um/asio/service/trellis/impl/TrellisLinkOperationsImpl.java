package es.um.asio.service.trellis.impl;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trellisldp.api.RuntimeTrellisException;

import com.jayway.restassured.response.Response;

import es.um.asio.abstractions.constants.Constants;
import es.um.asio.abstractions.domain.Operation;
import es.um.asio.service.service.discovery.DiscoveryClient;
import es.um.asio.service.service.uris.UrisFactoryClient;
import es.um.asio.service.trellis.TrellisCommonOperations;
import es.um.asio.service.trellis.TrellisLinkOperations;
import es.um.asio.service.util.MediaTypes;
import es.um.asio.service.util.RdfObjectMapper;
import es.um.asio.service.util.TriplesStorageUtils;

@Service
public class TrellisLinkOperationsImpl implements TrellisLinkOperations {

	/** The logger. */
	private final Logger logger = LoggerFactory.getLogger(TrellisLinkOperationsImpl.class);

	 /** The trellis url end point. */
    @Value("${app.trellis.endpoint}")
    private String trellisUrlEndPoint;
	
	@Autowired
	private TrellisCommonOperations trellisCommonOperations;
	
	  /** The trellis utils. */
    @Autowired
    private TriplesStorageUtils triplesStorageUtils;
    
    @Autowired
    private UrisFactoryClient urisFactoryClient;
    
    @Autowired
    private DiscoveryClient discoveryClient;

	

	/**
	 * Creates the links entry.
	 *
	 * @param message the message
	 * @return the model
	 */
	@Override
	public Model retrieveObjectFromTellis(String objectId, String className) {
		Model result = null;
		
		try {
			String localStorageUri = urisFactoryClient.getLocalStorageUriByEntityId(objectId, className);
			if(!StringUtils.isBlank(localStorageUri)) {
				result = getObjectFromTellis(localStorageUri);				
			} else {
				this.logger.error("Resource not found in Trellis with className: {} id: {}", className, objectId);
			}
		} catch (Exception e) {
			logger.error("Error retrieving class and id properties cause: {}", e.getMessage());
			logger.error("createLinksEntry:", e);
		}
		return result;
	}

	/**
	 * Gets the object from tellis.
	 *
	 * @param localStorageUri the local storage uri
	 * @return the object from tellis
	 */
	private Model getObjectFromTellis(final String localStorageUri) {
		
		Model result = null;
		try {
			Response response = trellisCommonOperations.createRequestSpecification().get(new URI(localStorageUri));
			String strModel = response.getBody().asString();			
			result = triplesStorageUtils.toObject(strModel, RDFLanguages.TURTLE);					
		} catch (Exception e) {
			this.logger.error("Invalid URI {}", localStorageUri);
			logger.error("getObjectFromTellis:", e);
		}
		
		return result;
	}

	
	/**
     * Update entry.
     *
     * @param message the message
     */
   
    public void updateLinksEntry(Model model, String localUri, String className) {
    	String urlContainer = localUri;
               
        Response postResponse = trellisCommonOperations.createRequestSpecification()
                .contentType(MediaTypes.TEXT_TURTLE)
                .body(model, new RdfObjectMapper()).put(urlContainer);
        
        if (postResponse.getStatusCode() != HttpStatus.SC_OK && postResponse.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
            
            logger.error("Error updating links entry cause: {}", postResponse.getBody().asString());
            throw new RuntimeTrellisException("Error updating in Trellis the object:".concat(localUri));
        } else {
        	
        	// we call the discovery library in order to notify the updating
        	this.discoveryClient.eventNotifyDiscovery(Operation.UPDATE, className, localUri, Constants.SUBDOMAIN_VALUE, Constants.TRELLIS);
        	
            logger.info("GRAYLOG-TS Actualizado recurso en trellis de tipo: {}", localUri);
        }        
    }
}
