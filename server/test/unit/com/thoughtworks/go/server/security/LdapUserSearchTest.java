/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.security;

import java.util.Arrays;
import javax.naming.directory.SearchControls;

import com.thoughtworks.go.config.LdapConfig;
import com.thoughtworks.go.config.SecurityConfig;
import com.thoughtworks.go.config.server.security.ldap.BaseConfig;
import com.thoughtworks.go.config.server.security.ldap.BasesConfig;
import com.thoughtworks.go.server.service.GoConfigService;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.springframework.ldap.core.AttributesMapperCallbackHandler;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.ldap.SpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.userdetails.UsernameNotFoundException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LdapUserSearchTest {

    private GoConfigService goConfigService;
    private ContextSource contextFactory;
    private LdapUserSearch ldapUserSearch;
    private SecurityConfig securityConfig;
    private LdapTemplate ldapTemplate;
    private Logger logger;
    private LdapUserSearch spy;

    @Before
    public void setUp() {
        goConfigService = mock(GoConfigService.class);
        contextFactory = mock(SpringSecurityContextSource.class);
        securityConfig = mock(SecurityConfig.class);
        ldapTemplate = mock(LdapTemplate.class);
        logger = mock(Logger.class);
        ldapUserSearch = new LdapUserSearch(goConfigService, contextFactory, ldapTemplate, logger);
        when(goConfigService.security()).thenReturn(securityConfig);
        spy = spy(ldapUserSearch);
    }

    @Test
    public void shouldThrowUserNameNotFoundExceptionWhenNoUserFound_WithOneSearchBase() {
        final FilterBasedLdapUserSearch filterBasedLdapUserSearch = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("search_base,foo")));
        doReturn(filterBasedLdapUserSearch).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().first().getValue(), ldapConfig.searchFilter());
        when(filterBasedLdapUserSearch.searchForUser("username")).thenThrow(new UsernameNotFoundException("User username not found in directory."));

        try {
            spy.searchForUser("username");
            fail("should have throw up");
        } catch (UsernameNotFoundException e) {
            assertThat(e.getMessage(), is("User username not found in directory."));
        }
        verify(filterBasedLdapUserSearch).searchForUser("username");
    }

    @Test
    public void shouldThrowUserNameNotFoundExceptionWhenNoUserFound_WithMultipleSearchBase() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));
        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        when(filter1.searchForUser("username")).thenThrow(new UsernameNotFoundException("User username not found in directory."));
        when(filter2.searchForUser("username")).thenThrow(new UsernameNotFoundException("User username not found in directory."));

        try {
            spy.searchForUser("username");
            fail("Should have thrown up");
        } catch (UsernameNotFoundException e) {
            assertThat(e.getMessage(), is("User username not found in directory."));
        }
        verify(filter1).searchForUser("username");
        verify(filter2).searchForUser("username");
    }

    @Test
    public void shouldReturnUserFoundInSecondSearchBase() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));
        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        when(filter1.searchForUser("username")).thenThrow(new UsernameNotFoundException("User username not found in directory."));
        DirContextOperations foundUser = mock(DirContextOperations.class);
        when(filter2.searchForUser("username")).thenReturn(foundUser);

        assertThat(spy.searchForUser("username"), is(foundUser));

        verify(filter1).searchForUser("username");
        verify(filter2).searchForUser("username");
    }

    @Test
    public void shouldNotProceedWithTheNextSearchBaseWhenUserIsFoundInOne() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        DirContextOperations foundUser = mock(DirContextOperations.class);
        when(filter1.searchForUser("username")).thenReturn(foundUser);

        assertThat(spy.searchForUser("username"), is(foundUser));

        verify(filter1).searchForUser("username");
        verify(filter2, never()).searchForUser("username");
    }

    @Test
    public void shouldSkipInvalidSearchBaseWhenAuthenticating() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        DirContextOperations foundUser = mock(DirContextOperations.class);
        when(filter1.searchForUser("username")).thenThrow(new RuntimeException("Invalid search base"));
        when(filter2.searchForUser("username")).thenReturn(foundUser);

        assertThat(spy.searchForUser("username"), is(foundUser));

        verify(filter1).searchForUser("username");
        verify(filter2).searchForUser("username");
    }

    @Test
    public void shouldLogErrorsDueToInvalidSearchBaseWhenAuthenticating() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        DirContextOperations foundUser = mock(DirContextOperations.class);
        RuntimeException runtimeException = new RuntimeException("Invalid search base");
        when(filter1.searchForUser("username")).thenThrow(runtimeException);
        when(filter2.searchForUser("username")).thenReturn(foundUser);

        assertThat(spy.searchForUser("username"), is(foundUser));

        verify(filter1).searchForUser("username");
        verify(filter2).searchForUser("username");
        verify(logger).warn("The ldap configuration for search base 'base1' is invalid", runtimeException);
    }

    @Test
    public void shouldLogErrorsForAllInvalidSearchBaseWhenAuthenticating() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter3 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter4 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2"), new BaseConfig("base3"), new BaseConfig("base4")));

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        doReturn(filter3).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(2).getValue(), ldapConfig.searchFilter());
        doReturn(filter4).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(3).getValue(), ldapConfig.searchFilter());
        DirContextOperations foundUser = mock(DirContextOperations.class);
        RuntimeException runtimeException = new RuntimeException("Invalid search base");
        when(filter1.searchForUser("username")).thenThrow(runtimeException);
        when(filter2.searchForUser("username")).thenThrow(new UsernameNotFoundException("User not found"));
        when(filter3.searchForUser("username")).thenThrow(runtimeException);
        when(filter4.searchForUser("username")).thenReturn(foundUser);

        assertThat(spy.searchForUser("username"), is(foundUser));

        verify(logger, times(1)).warn("The ldap configuration for search base 'base1' is invalid", runtimeException);
        verify(logger, times(1)).warn("The ldap configuration for search base 'base3' is invalid", runtimeException);
    }

    @Test
    public void shouldNotLogWhenLastSearchBaseIsInvalidAndUserIsNotFound() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        RuntimeException runtimeException = new RuntimeException("Invalid search base");
        when(filter1.searchForUser("username")).thenThrow(new UsernameNotFoundException("User not found"));
        when(filter2.searchForUser("username")).thenThrow(runtimeException);

        try {
            spy.searchForUser("username");
        } catch (RuntimeException e) {
            assertThat(e, is(runtimeException));
        }

        verify(logger, never()).warn(Matchers.<Object>any(), Matchers.<Throwable>any());
    }

    @Test
    public void shouldNotLogErrorsIfThereIsOnlyOneSearchBaseWhichIsInvalidWhenAuthenticating() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1")));
        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        RuntimeException runtimeException = new RuntimeException("Invalid search base");
        when(filter1.searchForUser("username")).thenThrow(runtimeException);

        try{
            spy.searchForUser("username");
        } catch (RuntimeException e) {
            assertThat(e, is(runtimeException));
        }

        verify(logger, never()).warn(Matchers.<Object>any(), Matchers.<Throwable>any());
    }

    @Test
    public void shouldNotLogWhenUserNameNotFoundExceptionIsThrown() {
        final FilterBasedLdapUserSearch filter1 = mock(FilterBasedLdapUserSearch.class);
        final FilterBasedLdapUserSearch filter2 = mock(FilterBasedLdapUserSearch.class);
        LdapConfig ldapConfig = setLdapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2")));
        DirContextOperations foundUser = mock(DirContextOperations.class);

        doReturn(filter1).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(0).getValue(), ldapConfig.searchFilter());
        doReturn(filter2).when(spy).getFilterBasedLdapUserSearch(ldapConfig.getBasesConfig().get(1).getValue(), ldapConfig.searchFilter());
        when(filter1.searchForUser("username")).thenThrow(new UsernameNotFoundException("User username not found in directory."));
        when(filter2.searchForUser("username")).thenReturn(foundUser);

        spy.searchForUser("username");

        verify(logger, never()).warn(Matchers.<Object>any(), Matchers.<Throwable>any());
    }

    @Test
    public void shouldThrowUpWhenNoSearchBaseIsConfigured() {
        setLdapConfig(new BasesConfig());

        try {
            spy.searchForUser("username");
            fail("This should throw exception mandating search bases.");
        } catch (Exception e){
            assertThat(e.getMessage(), is("No LDAP Search Bases are configured."));
        }
    }

    @Test
    public void shouldFilterForMatchingUsernamesInSearchBase() throws Exception {
        ldapUserSearch.search("username", ldapConfig(new BasesConfig(new BaseConfig("base1"))));
        verify(ldapTemplate).search(argThat(is("base1")),anyString(),any(SearchControls.class),any(AttributesMapperCallbackHandler.class));
    }

    @Test
    public void shouldFilterForMatchingUsernamesInMultipleBases() throws Exception {
        AttributesMapperCallbackHandler handler = mock(AttributesMapperCallbackHandler.class);
        doReturn(handler).when(spy).getAttributesMapperCallbackHandler();
        when(handler.getList()).thenReturn(Arrays.asList());

        spy.search("username", ldapConfig(new BasesConfig(new BaseConfig("base1"), new BaseConfig("base2"))));

        verify(handler).getList();
        verify(ldapTemplate).search(argThat(is("base1")), anyString(), any(SearchControls.class), eq(handler));
        verify(ldapTemplate).search(argThat(is("base2")), anyString(), any(SearchControls.class), eq(handler));
    }

    @Test
    public void shouldThrowExceptionWhenSearchingIfBaseSearchIsEmpty(){
        setLdapConfig(new BasesConfig());

        try {
            spy.search("username");
            fail("This should throw exception mandating search bases.");
        } catch (Exception e){
            assertThat(e.getMessage(), is("Atleast one Search Base needs to be configured."));
        }
    }

    private LdapConfig setLdapConfig(final BasesConfig basesConfig) {
        when(securityConfig.isSecurityEnabled()).thenReturn(true);
        LdapConfig ldapConfig = ldapConfig(basesConfig);
        when(securityConfig.ldapConfig()).thenReturn(ldapConfig);
        return ldapConfig;
    }

    private LdapConfig ldapConfig(BasesConfig basesConfig) {
        return new LdapConfig("url", "managerDN", "managerPassword", "encrypted", false, basesConfig, "searchFilter");
    }

}