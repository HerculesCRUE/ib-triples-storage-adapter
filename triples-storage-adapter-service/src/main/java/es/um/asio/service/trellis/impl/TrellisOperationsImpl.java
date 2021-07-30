package es.um.asio.service.trellis.impl;

import org.apache.http.HttpStatus;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trellisldp.api.RuntimeTrellisException;

import com.jayway.restassured.response.Response;

import es.um.asio.abstractions.constants.Constants;
import es.um.asio.abstractions.domain.ManagementBusEvent;
import es.um.asio.abstractions.domain.Operation;
import es.um.asio.abstractions.perfomance.WatchDog;
import es.um.asio.service.service.discovery.DiscoveryClient;
import es.um.asio.service.service.uris.UrisFactoryClient;
import es.um.asio.service.trellis.TrellisCommonOperations;
import es.um.asio.service.trellis.TrellisOperations;
import es.um.asio.service.trellis.util.TrellisCache;
import es.um.asio.service.util.MediaTypes;
import es.um.asio.service.util.RdfObjectMapper;
import es.um.asio.service.util.TriplesStorageUtils;

@Service
public class TrellisOperationsImpl implements TrellisOperations {

	/** The logger. */
    private final Logger logger = LoggerFactory.getLogger(TrellisOperationsImpl.class);
    
    /** The trellis utils. */
    @Autowired
    private TriplesStorageUtils triplesStorageUtils;
    
    @Autowired
    private TrellisCommonOperations trellisCommonOperations;
    
    @Autowired
    private UrisFactoryClient urisFactoryClient;
    
    @Autowired
    private DiscoveryClient discoveryClient;
    
    @Autowired
    private TrellisCache trellisCache;
    
    /** The trellis url end point. */
    @Value("${app.trellis.endpoint}")
    private String trellisUrlEndPoint;
    
       
    /**
     * Exists container.
     *
     * @param message the message
     * @return true, if successful
     */
    @Override
    public boolean existsContainer(ManagementBusEvent message) {
        Boolean result = (Boolean) trellisCache.find(message.getClassName(), Constants.CACHE_TRELLIS_CONTAINER);
        
        if(result == null || !result) {
        	String urlContainer =  trellisUrlEndPoint.concat("/").concat(message.getClassName());
        	Model model;
        	try {
        		model = trellisCommonOperations.createRequestSpecification()
        				.header("Accept", MediaTypes.TEXT_TURTLE)
        				.expect()
        				.when().get(urlContainer)
        				.as(Model.class, new RdfObjectMapper(urlContainer));
        		result = model.size() > 0;
        	} catch (Exception e) {
        		result = false;
        	} finally {
        		trellisCache.saveInCache(message.getClassName(), result, Constants.CACHE_TRELLIS_CONTAINER);				
			}       	
        }
                
        return result;  
    }

    /**
     * Creates the container.
     *
     * @param message the message
     */
    @Override
    public void createContainer(ManagementBusEvent message) {
        logger.info("Creating a container");
        WatchDog createContainerWatchDog = new WatchDog();
        
        Model model = ModelFactory.createDefaultModel();
        model.createProperty("http://hercules.org");
        Resource resourceProperties = model.createResource();
        Property a = model.createProperty("http://www.w3.org/ns/ldp#", "a");
        Property dcterms = model.createProperty("http://purl.org/dc/terms/", "title");
        
        resourceProperties.addProperty(a, "Container");
        resourceProperties.addProperty(a, "BasicContainer");
        resourceProperties.addProperty(dcterms, message.getClassName().concat(" Container"));
        
        Response postResponse;
        try {
            postResponse = trellisCommonOperations.createRequestSpecification()
                    .contentType(MediaTypes.TEXT_TURTLE)
                    .header("slug", message.getClassName())                 
                    .header("link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"")
                    .body(model, new RdfObjectMapper())
                    .post(trellisUrlEndPoint);
            
            if (postResponse.getStatusCode() != HttpStatus.SC_CREATED) {
                logger.warn("The container already exists: {}", postResponse.getStatusCode());
            } else {
                logger.info("GRAYLOG-TS Creado contenedor de tipo: {}", message.getClassName());
            }
        } catch (Exception e) {
            logger.error("createContainer:" , e);
        }
        
        createContainerWatchDog.takeTime("createContainer");
        
        // we print the watchdog results
        this.logger.warn("-----------------------------------------------------------------------");
        createContainerWatchDog.printnResults(this.logger);
        this.logger.warn("-----------------------------------------------------------------------");
    }

    /**
     * Creates the entry.
     *
     * @param message the message
     */
    @Override
    public void createEntry(ManagementBusEvent message) {
    	WatchDog createEntryWatchDog = new WatchDog();
    	
        Model model = triplesStorageUtils.toObject(message.getModel());
        String urlContainer =  trellisUrlEndPoint.concat("/").concat(message.getClassName());
        
        // we only retrieve the id 
        String id = message.getIdModel().split("/")[message.getIdModel().split("/").length - 1];
        String localTripleStorageUri = urlContainer.concat("/").concat(id);
        
        // we call to uri factory to notify the insertion
        logger.info("FactoryUriNotification: canonicalUri {}, localUri {}", message.getIdModel(), localTripleStorageUri);
        this.urisFactoryClient.eventNotifyUrisFactory(message.getIdModel(), localTripleStorageUri, Constants.TRELLIS);

        
        Response postResponse = trellisCommonOperations.createRequestSpecification()
                .contentType(MediaTypes.TEXT_TURTLE)
                .header("slug", id)
                .body(model, new RdfObjectMapper()).post(urlContainer);
        
        if (postResponse.getStatusCode() != HttpStatus.SC_CREATED) {
            logger.warn("Warn: saving the object: {}", message.getModel());
            logger.warn("Operation: {}", message.getOperation());
            logger.warn("cause: {}", postResponse.getBody().asString());
            logger.warn("Warn: saving in Trellis the object: {}",message.getModel());

            // FIXME ask Dani, Is the order important? Why don't put here the notification statements?
            
        } else {
            logger.info("GRAYLOG-TS Creado recurso en trellis de tipo: {}", message.getClassName());
            // we call the discovery library in order to notify the insertion
            try {
                this.discoveryClient.eventNotifyDiscovery(Operation.INSERT, message.getClassName(), localTripleStorageUri, Constants.SUBDOMAIN_VALUE, Constants.TRELLIS);
                logger.info("Notified {} in class: {} localURI: {} to discovery lib",Operation.INSERT,message.getClassName(),localTripleStorageUri);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error in comunicate with discovery lib");
            }
        }
        
        createEntryWatchDog.takeTime("createEntry");
        
        // we print the watchdog results
        this.logger.warn("-----------------------------------------------------------------------");
        createEntryWatchDog.printnResults(this.logger);
        this.logger.warn("-----------------------------------------------------------------------");
    }

    /**
     * Update entry.
     *
     * @param message the message
     */
    @Override
    public void updateEntry(ManagementBusEvent message) {
    	WatchDog updateEntryWatchDog = new WatchDog();
        String resourceID = message.getIdModel().split("/")[message.getIdModel().split("/").length - 1];
        String urlContainer =  trellisUrlEndPoint.concat("/").concat(message.getClassName()).concat("/").concat(resourceID);
        
        Model model = triplesStorageUtils.toObject(message.getModel());        
        Response postResponse = trellisCommonOperations.createRequestSpecification()
                .contentType(MediaTypes.TEXT_TURTLE)
                .body(model, new RdfObjectMapper()).put(urlContainer);
        
        if (postResponse.getStatusCode() != HttpStatus.SC_OK && postResponse.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
            logger.error("Error updating the object: {}",message.getModel());
            logger.error("Operation: {}", message.getOperation());
            logger.error("cause: {}",postResponse.getBody().asString());
            throw new RuntimeTrellisException("Error updating in Trellis the object: ".concat(message.getModel()));
        } else {
            logger.info("GRAYLOG-TS Actualizado recurso en trellis de tipo: {}",message.getClassName());
            try {
                // we call the discovery library in order to notify the insertion
                this.discoveryClient.eventNotifyDiscovery(Operation.UPDATE, message.getClassName(), urlContainer, Constants.SUBDOMAIN_VALUE, Constants.TRELLIS);
                logger.info("Notified {} in class: {} localURI: {} to discovery lib",Operation.UPDATE,message.getClassName(),urlContainer);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error in comunicate with discovery lib");
            }
        }
        
        updateEntryWatchDog.takeTime("updateEntry");
        
        // we print the watchdog results
        this.logger.warn("-----------------------------------------------------------------------");
        updateEntryWatchDog.printnResults(this.logger);
        this.logger.warn("-----------------------------------------------------------------------");
    }

    /**
     * Delete entry.
     *
     * @param message the message
     */
    @Override
    public void deleteEntry(ManagementBusEvent message) {
    	WatchDog deleteEntryWatchDog = new WatchDog();
        String resourceID = message.getIdModel().split("/")[message.getIdModel().split("/").length - 1];
        String urlContainer =  trellisUrlEndPoint.concat("/").concat(message.getClassName()).concat("/").concat(resourceID);
        
        Response deleteResponse = trellisCommonOperations.createRequestSpecification().delete(urlContainer);
       
        if (deleteResponse.getStatusCode() != HttpStatus.SC_OK && deleteResponse.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
            logger.error("Error deleting the object: {} - {}", message.getClassName(), message.getIdModel());
            logger.error("Operation: {}", message.getOperation());
            logger.error("cause: {}", deleteResponse.getBody().asString());
            throw new RuntimeTrellisException("Error deleting in Trellis the object: ".concat(message.getClassName()).concat(" - ").concat(message.getIdModel()));
        } else {
            logger.info("GRAYLOG-TS Eliminado recurso en trellis de tipo: {}", message.getClassName());
            try {
                // we call the discovery library in order to notify the insertion
                this.discoveryClient.eventNotifyDiscovery(Operation.DELETE, message.getClassName(), urlContainer, Constants.SUBDOMAIN_VALUE, Constants.TRELLIS);
                logger.info("Notified {} in class: {} localURI: {} to discovery lib",Operation.DELETE,message.getClassName(),urlContainer);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error in comunicate with discovery lib");
            }
        }
        
        deleteEntryWatchDog.takeTime("deleteEntry");
        
        // we print the watchdog results
        this.logger.warn("-----------------------------------------------------------------------");
        deleteEntryWatchDog.printnResults(this.logger);
        this.logger.warn("-----------------------------------------------------------------------");
    }
}
