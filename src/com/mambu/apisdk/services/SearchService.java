/**
 * 
 */
package com.mambu.apisdk.services;

// import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.mambu.apisdk.MambuAPIService;
import com.mambu.apisdk.exception.MambuApiException;
import com.mambu.apisdk.util.APIData;
import com.mambu.apisdk.util.ApiDefinition;
import com.mambu.apisdk.util.ApiDefinition.ApiType;
import com.mambu.apisdk.util.ParamsMap;
import com.mambu.apisdk.util.ServiceExecutor;
import com.mambu.core.shared.model.SearchResult;

/**
 * Service class which handles the API operations available for the Search
 * 
 * @author mdanilkis
 * 
 */

public class SearchService {

	// Param names for QUERY and TYPE
	private String QUERY = APIData.QUERY;
	private String SEARCH_TYPES = APIData.SEARCH_TYPES;

	private static final String LIMIT = APIData.LIMIT;

	private ServiceExecutor serviceExecutor;
	private final static ApiDefinition searchEntitiies = new ApiDefinition(ApiType.GET_LIST, SearchResult.class);

	/***
	 * Create a new search service
	 * 
	 * @param mambuAPIService
	 *            the service responsible with the connection to the server
	 */
	@Inject
	public SearchService(MambuAPIService mambuAPIService) {
		this.serviceExecutor = new ServiceExecutor(mambuAPIService);
	}

	/***
	 * Get a Map of search results <SearchResul, List<SearchResult> for a given query and an optional list of search
	 * types
	 * 
	 * @param query
	 *            the string to query
	 * @param searchTypes
	 *            list, in brackets,separated by comma of search types to query. E.g. [CLIENT, GROUP]. Null if searching
	 *            for all types (defined by SearchResult.Type). The results of the query shall be limited to the
	 *            specified types
	 * @param limit
	 *            maximum number of results to return. If null, Mambu defaults this to 100.
	 * 
	 * 
	 * @return Map<SearchResult.Type, List<SearchResult>> is returned. Empty map and/or Mambu exception if not found
	 * 
	 * @throws MambuApiException
	 */
	public Map<SearchResult.Type, List<SearchResult>> search(String query, List<SearchResult.Type> searchTypes,
			String limit) throws MambuApiException {

		if (query == null) {
			throw new IllegalArgumentException("Query must not be null");
		}
		// strip possible blank chars
		query = query.trim();

		ParamsMap paramsMap = new ParamsMap();
		paramsMap.addParam(QUERY, query);
		paramsMap.addParam(LIMIT, limit);

		// Add search Types, if any
		if (searchTypes != null && searchTypes.size() > 0) {
			String typeParamsString = new String("[");
			for (int i = 0; i < searchTypes.size(); i++) {
				// a comma separated list of Search Types, e.g. GROUP,CLIENT, LOAN_ACCOUNT
				if (i > 0)
					typeParamsString = typeParamsString.concat(",");
				typeParamsString = typeParamsString.concat(searchTypes.get(i).toString());
			}
			typeParamsString = typeParamsString.concat("]");
			paramsMap.addParam(SEARCH_TYPES, typeParamsString);
		}

		return serviceExecutor.execute(searchEntitiies, paramsMap);

	}
}
