import com.manydesigns.portofino.PortofinoProperties
import com.manydesigns.portofino.application.Application
import com.manydesigns.portofino.shiro.AbstractApplicationRealmDelegate
import com.manydesigns.portofino.shiro.ApplicationRealm
import com.manydesigns.portofino.shiro.GroupPermission
import org.apache.commons.configuration.Configuration
import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.authc.SimpleAuthenticationInfo
import org.apache.shiro.authz.AuthorizationInfo
import org.apache.shiro.authz.Permission
import org.apache.shiro.authz.SimpleAuthorizationInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Security extends AbstractApplicationRealmDelegate {

    private static final Logger logger = LoggerFactory.getLogger(Security.class);

    private static final String ADMIN_LOGIN = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @Override
    protected Collection<String> loadAuthorizationInfo(ApplicationRealm realm, String principal) {
        if (ADMIN_LOGIN.equals(principal)) {
            return [ getAdministratorsGroup(realm) ]
        } else {
            return []
        }
    }

    AuthenticationInfo getAuthenticationInfo(ApplicationRealm realm, String userName, String password) {
        if (ADMIN_LOGIN.equals(userName) && ADMIN_PASSWORD.equals(password)) {
            SimpleAuthenticationInfo info =
                    new SimpleAuthenticationInfo(userName, password.toCharArray(), realm.name);
            return info;
        } else {
            throw new AuthenticationException("Login failed");
        }
    }

    Set<String> getUsers(ApplicationRealm realm) {
        Set<String> result = new LinkedHashSet<String>();
        result.add(ADMIN_LOGIN);
        return result;
    }

}
