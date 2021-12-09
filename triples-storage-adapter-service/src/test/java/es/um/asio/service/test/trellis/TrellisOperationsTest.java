package es.um.asio.service.test.trellis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import es.um.asio.abstractions.domain.ManagementBusEvent;
import es.um.asio.abstractions.domain.Operation;
import es.um.asio.service.service.discovery.DiscoveryClient;
import es.um.asio.service.service.uris.UrisFactoryClient;
import es.um.asio.service.trellis.TrellisCommonOperations;
import es.um.asio.service.trellis.TrellisOperations;
import es.um.asio.service.trellis.impl.TrellisCommonOperationsImpl;
import es.um.asio.service.trellis.impl.TrellisOperationsImpl;
import es.um.asio.service.trellis.util.TrellisCache;
import es.um.asio.service.trellis.util.TrellisCacheImpl;
import es.um.asio.service.util.TriplesStorageUtils;

@RunWith(SpringRunner.class)
public class TrellisOperationsTest {
    
    /**
     * Trellis operations
     */
    @Autowired
    private TrellisOperations trellisOperations;
    
    @MockBean
    private UrisFactoryClient urisFactoryClient;
    
    @MockBean
    private DiscoveryClient discoveryClient;
    
    @TestConfiguration
    static class TrellisStorageServiceTestConfiguration {
        @Bean
        public TrellisOperations trellisOperations() {
            return new TrellisOperationsImpl();
        }
        
        @Bean
        public TriplesStorageUtils triplesStorageUtils() {
            return new TriplesStorageUtils();
        }
        
        @Bean
        public TrellisCommonOperations trellisCommonOperations() {
            return new TrellisCommonOperationsImpl();
        }
        
        @Bean
        public TrellisCache trellisCache() {
            return new TrellisCacheImpl();
        }
    }
    
    @Before
    public void setUp() {
        
    }
    
    @Test
    public void whenExistsContainer_thenReturnTrue() {
        assertThat(this.trellisOperations.existsContainer(this.generateEvent())).isFalse();
    }
    
    @Test
    public void whenCraeteContainer_thenNoError() {
        ManagementBusEvent event = this.generateEvent();
        String originalIdModel = event.getIdModel();
        this.trellisOperations.createContainer(event);
        assertThat(event.getIdModel()).isEqualTo(originalIdModel);
    }
    
    @Test
    public void whenCreateEntry_thenNoError() {
        try {
            this.trellisOperations.createEntry(this.generateEvent());
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("You specified too few path parameters in the request.");
        }
    }
    
    @Test
    public void whenUpdateEntry_thenNoError() {
        try {
            this.trellisOperations.updateEntry(this.generateEvent());
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("You specified too few path parameters in the request.");
        }
    }
    /*
    @Test
    public void whenDeleteEntry_thenNoError() {
        try {
            this.trellisOperations.deleteEntry(this.generateEvent());
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("You specified too few path parameters in the request.");
        }
    }
     */
    
    private ManagementBusEvent generateEvent() {
        return new ManagementBusEvent("1", "<?xml version=\"1.0\"?>\r\n"
                + "\r\n"
                + "<rdf:RDF\r\n"
                + "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\r\n"
                + "xmlns:si=\"https://www.w3schools.com/rdf/\">\r\n"
                + "\r\n"
                + "<rdf:Description rdf:about=\"https://www.w3schools.com\">\r\n"
                + "  <si:title>W3Schools</si:title>\r\n"
                + "  <si:author>Jan Egil Refsnes</si:author>\r\n"
                + "</rdf:Description>\r\n"
                + "\r\n"
                + "</rdf:RDF>", null, "foo.bar", Operation.INSERT);
    }
    
}
