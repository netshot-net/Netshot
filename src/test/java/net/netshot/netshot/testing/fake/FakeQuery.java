/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.testing.fake;

import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Remove;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.SharedSessionContract;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.query.BindableType;
import org.hibernate.query.KeyedPage;
import org.hibernate.query.KeyedResultList;
import org.hibernate.query.Order;
import org.hibernate.query.ParameterMetadata;
import org.hibernate.query.Query;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.ResultListTransformer;
import org.hibernate.query.SelectionQuery;
import org.hibernate.query.TupleTransformer;
import org.hibernate.query.spi.QueryOptions;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;

public class FakeQuery<R> implements Query<R> {

	private final Map<String, Object> parameters = new HashMap<>();

	public Map<String, Object> getParameterHash() {
		return this.parameters;
	}

	@Override
	public R getSingleResultOrNull() {
		throw new UnsupportedOperationException("Unimplemented method 'getSingleResultOrNull'");
	}

	@Override
	public long getResultCount() {
		throw new UnsupportedOperationException("Unimplemented method 'getResultCount'");
	}

	@Override
	public KeyedResultList<R> getKeyedResultList(KeyedPage<R> page) {
		throw new UnsupportedOperationException("Unimplemented method 'getKeyedResultList'");
	}

	@Override
	public Integer getFetchSize() {
		throw new UnsupportedOperationException("Unimplemented method 'getFetchSize'");
	}

	@Override
	public boolean isReadOnly() {
		throw new UnsupportedOperationException("Unimplemented method 'isReadOnly'");
	}

	@Override
	public int getMaxResults() {
		throw new UnsupportedOperationException("Unimplemented method 'getMaxResults'");
	}

	@Override
	public int getFirstResult() {
		throw new UnsupportedOperationException("Unimplemented method 'getFirstResult'");
	}

	@Override
	public CacheMode getCacheMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getCacheMode'");
	}

	@Override
	public CacheStoreMode getCacheStoreMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getCacheStoreMode'");
	}

	@Override
	public CacheRetrieveMode getCacheRetrieveMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getCacheRetrieveMode'");
	}

	@Override
	public boolean isCacheable() {
		throw new UnsupportedOperationException("Unimplemented method 'isCacheable'");
	}

	@Override
	public boolean isQueryPlanCacheable() {
		throw new UnsupportedOperationException("Unimplemented method 'isQueryPlanCacheable'");
	}

	@Override
	public SelectionQuery<R> setQueryPlanCacheable(boolean queryPlanCacheable) {
		throw new UnsupportedOperationException("Unimplemented method 'setQueryPlanCacheable'");
	}

	@Override
	public String getCacheRegion() {
		throw new UnsupportedOperationException("Unimplemented method 'getCacheRegion'");
	}

	@Override
	public LockModeType getLockMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getLockMode'");
	}

	@Override
	public LockMode getHibernateLockMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getHibernateLockMode'");
	}

	@Override
	public SelectionQuery<R> setHibernateLockMode(LockMode lockMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setHibernateLockMode'");
	}

	@Override
	public @Remove SelectionQuery<R> setAliasSpecificLockMode(String alias, LockMode lockMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setAliasSpecificLockMode'");
	}

	@Override
	public SelectionQuery<R> setFollowOnLocking(boolean enable) {
		throw new UnsupportedOperationException("Unimplemented method 'setFollowOnLocking'");
	}

	@Override
	public FlushModeType getFlushMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getFlushMode'");
	}

	@Override
	public FlushMode getHibernateFlushMode() {
		throw new UnsupportedOperationException("Unimplemented method 'getHibernateFlushMode'");
	}

	@Override
	public Integer getTimeout() {
		throw new UnsupportedOperationException("Unimplemented method 'getTimeout'");
	}

	@Override
	public Map<String, Object> getHints() {
		throw new UnsupportedOperationException("Unimplemented method 'getHints'");
	}

	@Override
	public Parameter<?> getParameter(String arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameter'");
	}

	@Override
	public Parameter<?> getParameter(int arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameter'");
	}

	@Override
	public <T> Parameter<T> getParameter(String arg0, Class<T> arg1) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameter'");
	}

	@Override
	public <T> Parameter<T> getParameter(int arg0, Class<T> arg1) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameter'");
	}

	@Override
	public <T> T getParameterValue(Parameter<T> arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameterValue'");
	}

	@Override
	public Object getParameterValue(String arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameterValue'");
	}

	@Override
	public Object getParameterValue(int arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'getParameterValue'");
	}

	@Override
	public Set<Parameter<?>> getParameters() {
		throw new UnsupportedOperationException("Unimplemented method 'getParameters'");
	}

	@Override
	public boolean isBound(Parameter<?> arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'isBound'");
	}

	@Override
	public <T> T unwrap(Class<T> arg0) {
		throw new UnsupportedOperationException("Unimplemented method 'unwrap'");
	}

	@Override
	public List<R> list() {
		throw new UnsupportedOperationException("Unimplemented method 'list'");
	}

	@Override
	public ScrollableResults<R> scroll() {
		throw new UnsupportedOperationException("Unimplemented method 'scroll'");
	}

	@Override
	public ScrollableResults<R> scroll(ScrollMode scrollMode) {
		throw new UnsupportedOperationException("Unimplemented method 'scroll'");
	}

	@Override
	public R uniqueResult() {
		throw new UnsupportedOperationException("Unimplemented method 'uniqueResult'");
	}

	@Override
	public R getSingleResult() {
		throw new UnsupportedOperationException("Unimplemented method 'getSingleResult'");
	}

	@Override
	public Optional<R> uniqueResultOptional() {
		throw new UnsupportedOperationException("Unimplemented method 'uniqueResultOptional'");
	}

	@Override
	public int executeUpdate() {
		throw new UnsupportedOperationException("Unimplemented method 'executeUpdate'");
	}

	@Override
	public SharedSessionContract getSession() {
		throw new UnsupportedOperationException("Unimplemented method 'getSession'");
	}

	@Override
	public String getQueryString() {
		throw new UnsupportedOperationException("Unimplemented method 'getQueryString'");
	}

	@Override
	public Query<R> applyGraph(@SuppressWarnings("rawtypes") RootGraph graph, GraphSemantic semantic) {
		throw new UnsupportedOperationException("Unimplemented method 'applyGraph'");
	}

	@Override
	public String getComment() {
		throw new UnsupportedOperationException("Unimplemented method 'getComment'");
	}

	@Override
	public Query<R> setComment(String comment) {
		throw new UnsupportedOperationException("Unimplemented method 'setComment'");
	}

	@Override
	public Query<R> addQueryHint(String hint) {
		throw new UnsupportedOperationException("Unimplemented method 'addQueryHint'");
	}

	@Override
	public LockOptions getLockOptions() {
		throw new UnsupportedOperationException("Unimplemented method 'getLockOptions'");
	}

	@Override
	public Query<R> setLockOptions(LockOptions lockOptions) {
		throw new UnsupportedOperationException("Unimplemented method 'setLockOptions'");
	}

	@Override
	public Query<R> setLockMode(String alias, LockMode lockMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setLockMode'");
	}

	@Override
	public <T> Query<T> setTupleTransformer(TupleTransformer<T> transformer) {
		throw new UnsupportedOperationException("Unimplemented method 'setTupleTransformer'");
	}

	@Override
	public Query<R> setResultListTransformer(ResultListTransformer<R> transformer) {
		throw new UnsupportedOperationException("Unimplemented method 'setResultListTransformer'");
	}

	@Override
	public QueryOptions getQueryOptions() {
		throw new UnsupportedOperationException("Unimplemented method 'getQueryOptions'");
	}

	@Override
	public ParameterMetadata getParameterMetadata() {
		throw new UnsupportedOperationException("Unimplemented method 'getParameterMetadata'");
	}

	@Override
	public Query<R> setParameter(String parameter, Object argument) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public <P> Query<R> setParameter(String parameter, P argument, Class<P> type) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public <P> Query<R> setParameter(String parameter, P argument, BindableType<P> type) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public Query<R> setParameter(String parameter, Instant argument, TemporalType temporalType) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public Query<R> setParameter(String parameter, Calendar argument, TemporalType temporalType) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public Query<R> setParameter(String parameter, Date argument, TemporalType temporalType) {
		this.parameters.put(parameter, argument);
		return this;
	}

	@Override
	public Query<R> setParameter(int parameter, Object argument) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <P> Query<R> setParameter(int parameter, P argument, Class<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <P> Query<R> setParameter(int parameter, P argument, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameter(int parameter, Instant argument, TemporalType temporalType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameter(int parameter, Date argument, TemporalType temporalType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameter(int parameter, Calendar argument, TemporalType temporalType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <T> Query<R> setParameter(QueryParameter<T> parameter, T argument) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <P> Query<R> setParameter(QueryParameter<P> parameter, P argument, Class<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <P> Query<R> setParameter(QueryParameter<P> parameter, P argument, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public <T> Query<R> setParameter(Parameter<T> parameter, T argument) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameter(Parameter<Calendar> parameter, Calendar argument, TemporalType temporalType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameter(Parameter<Date> parameter, Date argument, TemporalType temporalType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameter'");
	}

	@Override
	public Query<R> setParameterList(String parameter, @SuppressWarnings("rawtypes") Collection arguments) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(String parameter, Collection<? extends P> arguments, Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(String parameter, Collection<? extends P> arguments, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public Query<R> setParameterList(String parameter, Object[] values) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(String parameter, P[] arguments, Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(String parameter, P[] arguments, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public Query<R> setParameterList(int parameter, @SuppressWarnings("rawtypes") Collection arguments) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(int parameter, Collection<? extends P> arguments, Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(int parameter, Collection<? extends P> arguments, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public Query<R> setParameterList(int parameter, Object[] arguments) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(int parameter, P[] arguments, Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(int parameter, P[] arguments, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> arguments) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> arguments,
		Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, Collection<? extends P> arguments,
		BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, P[] arguments) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, P[] arguments, Class<P> javaType) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public <P> Query<R> setParameterList(QueryParameter<P> parameter, P[] arguments, BindableType<P> type) {
		throw new UnsupportedOperationException("Unimplemented method 'setParameterList'");
	}

	@Override
	public Query<R> setProperties(Object bean) {
		throw new UnsupportedOperationException("Unimplemented method 'setProperties'");
	}

	@Override
	public Query<R> setProperties(@SuppressWarnings("rawtypes") Map bean) {
		throw new UnsupportedOperationException("Unimplemented method 'setProperties'");
	}

	@Override
	public Query<R> setHibernateFlushMode(FlushMode flushMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setHibernateFlushMode'");
	}

	@Override
	public Query<R> setCacheable(boolean cacheable) {
		throw new UnsupportedOperationException("Unimplemented method 'setCacheable'");
	}

	@Override
	public Query<R> setCacheRegion(String cacheRegion) {
		throw new UnsupportedOperationException("Unimplemented method 'setCacheRegion'");
	}

	@Override
	public Query<R> setCacheMode(CacheMode cacheMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setCacheMode'");
	}

	@Override
	public Query<R> setCacheStoreMode(CacheStoreMode cacheStoreMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setCacheStoreMode'");
	}

	@Override
	public Query<R> setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setCacheRetrieveMode'");
	}

	@Override
	public Query<R> setTimeout(int timeout) {
		throw new UnsupportedOperationException("Unimplemented method 'setTimeout'");
	}

	@Override
	public Query<R> setFetchSize(int fetchSize) {
		throw new UnsupportedOperationException("Unimplemented method 'setFetchSize'");
	}

	@Override
	public Query<R> setReadOnly(boolean readOnly) {
		throw new UnsupportedOperationException("Unimplemented method 'setReadOnly'");
	}

	@Override
	public Query<R> setMaxResults(int maxResult) {
		throw new UnsupportedOperationException("Unimplemented method 'setMaxResults'");
	}

	@Override
	public Query<R> setFirstResult(int startPosition) {
		throw new UnsupportedOperationException("Unimplemented method 'setFirstResult'");
	}

	@Override
	public Query<R> setHint(String hintName, Object value) {
		throw new UnsupportedOperationException("Unimplemented method 'setHint'");
	}

	@Override
	public Query<R> setEntityGraph(EntityGraph<R> graph, GraphSemantic semantic) {
		throw new UnsupportedOperationException("Unimplemented method 'setEntityGraph'");
	}

	@Override
	public Query<R> enableFetchProfile(String profileName) {
		throw new UnsupportedOperationException("Unimplemented method 'enableFetchProfile'");
	}

	@Override
	public Query<R> disableFetchProfile(String profileName) {
		throw new UnsupportedOperationException("Unimplemented method 'disableFetchProfile'");
	}

	@Override
	public Query<R> setFlushMode(FlushModeType flushMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setFlushMode'");
	}

	@Override
	public Query<R> setLockMode(LockModeType lockMode) {
		throw new UnsupportedOperationException("Unimplemented method 'setLockMode'");
	}

	@Override
	public Query<R> setOrder(List<Order<? super R>> orderList) {
		throw new UnsupportedOperationException("Unimplemented method 'setOrder'");
	}

	@Override
	public Query<R> setOrder(Order<? super R> order) {
		throw new UnsupportedOperationException("Unimplemented method 'setOrder'");
	}


}
