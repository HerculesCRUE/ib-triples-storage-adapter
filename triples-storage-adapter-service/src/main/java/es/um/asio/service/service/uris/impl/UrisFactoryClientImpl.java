package es.um.asio.service.service.uris.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import es.um.asio.abstractions.constants.Constants;
import es.um.asio.abstractions.perfomance.WatchDog;
import es.um.asio.abstractions.storage.StorageType;
import es.um.asio.service.service.uris.UrisFactoryClient;
import es.um.asio.service.trellis.util.TrellisCache;

@Service
public class UrisFactoryClientImpl implements UrisFactoryClient {

	private final Logger logger = LoggerFactory.getLogger(UrisFactoryClientImpl.class);

	/** The local resource storage uri. */
	@Value("${app.generator-uris.endpoint-local-resource-storage-uri}")
	private String localResourceStorageUri;

	/** The local property storage uri. */
	@Value("${app.generator-uris.endpoint-local-property-storage-uri}")
	private String localPropertyStorageUri;

	/** The uri property. */
	@Value("${app.generator-uris.endpoint-property}")
	private String uriProperty;

	/** The uri factory endpoint. */
	@Value("${app.generator-uris.endpoint-link-uri}")
	private String uriFactoryEndpoint;
	
	@Value("${app.domain}")
	private String domain;

	/** Rest Template. */
	@Autowired
	private RestTemplate restUrisTemplate;

	@Autowired
	private TrellisCache trellisCache;

	@Bean
	public RestTemplate restUrisTemplate() {
		return new RestTemplate();
	}

	public String getLocalStorageUriByEntityId(String entityId, String className) {
		this.logger.warn("getLocalStorageUriByEntityId entityId={}, className={}", entityId, className);
		return getUriHelper(entityId, className, Constants.LOCAL_Uri);
	}

	/**
	 * Gets the canonical uri by resource.
	 *
	 * @param id        the id
	 * @param className the class name
	 * @return the canonical uri by resource
	 */
	public String getCanonicalUriByResource(String id, String className) {
		this.logger.warn("getCanonicalUriByResource id={}, className={}", id, className);
		return getUriHelper(id, className, Constants.CANONICAL_URI_LANGUAGE_STR);
	}

	private String getUriHelper(String id, String className, String typeUri) {
		this.logger.warn("getUriHelper 2 id={}, className={}, typeUri={}", id, className, typeUri);
		Object result = this.trellisCache.find(this.buildKeyForCanonicalLocalUri(id, className),
				Constants.CACHE_CANONICAL_LOCAL_URIS);
		if (result != null && result instanceof LinkedHashMap) {
			return (String) ((LinkedHashMap<String, Object>) result).get(typeUri);
		} else {
			return this.getUriByResource(id, className, typeUri, Constants.REFERENCE);
		}
	}

	/**
	 * Gets the uri by resource.
	 *
	 * @param id        the id
	 * @param className the class name
	 * @param typeURI   the type URI
	 * @return the uri by resource
	 */
	private String getUriByResource(String id, String className, String typeURI, String typeId) {

		logger.warn("getUriByResource(id={}, className={}, typeURI={}, typeId={})", id, className, typeURI, typeId);

		String result = StringUtils.EMPTY;
		try {
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(localResourceStorageUri)
					.queryParam(Constants.DOMAIN, StringUtils.isNotBlank(this.domain) ? this.domain : Constants.DOMAIN_VALUE)
					.queryParam(Constants.SUBDOMAIN, Constants.SUBDOMAIN_VALUE)
					.queryParam(Constants.LANG, Constants.SPANISH_LANGUAGE)
					.queryParam(Constants.TYPE_CODE, Constants.TYPE_REST).queryParam("entity", className)
					.queryParam(typeId, id)
					.queryParam(Constants.STORAGE_NAME, StorageType.TRELLIS.name().toLowerCase());

			this.logger.info("[getUriByResource] Call url {}", builder.toUriString());
			
			Map response = restUrisTemplate.getForObject(builder.toUriString(), Map.class);
			if (response != null) {
				ArrayList urisMap = (ArrayList) response.get(Constants.LOCAL_URIS);
				result = (String) ((LinkedHashMap<String, Object>) urisMap.get(0)).get(typeURI);

				// we save in cache
				this.trellisCache.saveInCache(this.buildKeyForCanonicalLocalUri(id, className), urisMap.get(0),
						Constants.CACHE_CANONICAL_LOCAL_URIS);
			}
		} catch (RestClientException e) {
			logger.error("Error retrieving getUriByResource(id={}, class={}) ", id, className);
			logger.error("getUriByResource", e);
		}

		logger.info("LocalStorageUri(id={}, className={}) = {}", id, className, result);

		return result;
	}

	/**
	 * Creates the property.
	 *
	 * @param fieldName the field name
	 * @return the string
	 */
	@Override
	public String createProperty(String fieldName) {
		String result = StringUtils.EMPTY;

		Object cachedResult = this.trellisCache.find(fieldName, Constants.CACHE_PROPERTIES);
		if (cachedResult == null) {
			try {
				UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(uriProperty)
						.queryParam(Constants.DOMAIN, StringUtils.isNotBlank(this.domain) ? this.domain : Constants.DOMAIN_VALUE)
						.queryParam(Constants.SUBDOMAIN, Constants.SUBDOMAIN_VALUE)
						.queryParam(Constants.LANG, Constants.SPANISH_LANGUAGE);

				Map<String, String> obj = new HashMap<>();
				obj.put("property", fieldName);

				Map response = restUrisTemplate.postForObject(builder.toUriString(), obj, Map.class);
				if (response != null) {
					result = (String) response.get(Constants.CANONICAL_LANGUAGE_URI);

					// we save the value in the cache
					this.trellisCache.saveInCache(fieldName, result, Constants.CACHE_PROPERTIES);
				}
			} catch (RestClientException e) {
				logger.error("Error creating property {} cause: {} ", fieldName, e.getMessage());
				logger.error("createProperty", e);
			}
		} else {
			result = (String) cachedResult;
		}

		return result;
	}

	/**
	 * Event notify uris factory.
	 *
	 * @param canonicalUri the canonical uri
	 * @param localUri     the local uri
	 */
	public void eventNotifyUrisFactory(String cannonicalLanguageURI, String localURI, String triplesStoreTarget) {
		WatchDog eventNotifyWatchDog = new WatchDog();

		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(uriFactoryEndpoint)
				.queryParam(Constants.CANONICAL_LANGUAGE_URI, cannonicalLanguageURI)
				.queryParam(Constants.LOCAL_URI, localURI).queryParam(Constants.STORAGE_NAME, triplesStoreTarget);

		Map<String, String> obj = new HashMap<>();
		obj.put(Constants.CANONICAL_LANGUAGE_URI, cannonicalLanguageURI);
		obj.put(Constants.LOCAL_URI, localURI);
		obj.put(Constants.STORAGE_NAME, triplesStoreTarget);

		restUrisTemplate.postForObject(builder.toUriString(), obj, Object.class);

		eventNotifyWatchDog.takeTime("eventNotify");

		// we print the watchdog results
		this.logger.warn("-----------------------------------------------------------------------");
		eventNotifyWatchDog.printnResults(this.logger);
		this.logger.warn("-----------------------------------------------------------------------");
	}

	private String buildKeyForCanonicalLocalUri(String entityId, String className) {
		return entityId + "-" + className;
	}
}
