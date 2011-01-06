package hudson.plugins.openid;

import hudson.Extension;
import hudson.security.FederatedLoginService;
import hudson.security.FederatedLoginServiceUserProperty;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.consumer.InMemoryNonceVerifier;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class OpenIdLoginService extends FederatedLoginService {
    private final ConsumerManager manager;

    public OpenIdLoginService() throws ConsumerException {
        manager = new ConsumerManager();
        manager.setAssociations(new InMemoryConsumerAssociationStore());
        manager.setNonceVerifier(new InMemoryNonceVerifier(5000));
    }

    @Override
    public String getUrlName() {
        return "openid";
    }

    public Class<? extends FederatedLoginServiceUserProperty> getUserPropertyClass() {
        return OpenIdUserProperty.class;
    }

    /**
     * Commence a login.
     */
    public HttpResponse doStartLogin(@QueryParameter String openid, @QueryParameter final String from) throws OpenIDException, IOException {
        OpenIdSession s = new OpenIdSession(manager,openid,"federatedLoginService/openid/finish") {
            @Override
            protected HttpResponse onSuccess(Identity identity) throws IOException {
                try {
                    new IdentityImpl(identity).signin();
                    return HttpResponses.redirectToContextRoot();
                } catch (UnclaimedIdentityException e) {
                    // TODO: initiate the sign up
                    throw new UnsupportedOperationException();
                }
            }
        };
        Stapler.getCurrentRequest().getSession().setAttribute(SESSION_NAME,s);
        return s.doCommenceLogin();
    }

    public HttpResponse doFinish(StaplerRequest request) throws IOException, OpenIDException {
        OpenIdSession session = (OpenIdSession) Stapler.getCurrentRequest().getSession().getAttribute(SESSION_NAME);
        if (session==null)  return HttpResponses.error(StaplerResponse.SC_BAD_REQUEST,new Exception("no session"));
        return session.doFinishLogin(request);
    }

    public HttpResponse doStartAssociate(@QueryParameter String openid) throws OpenIDException, IOException {
        OpenIdSession s = new OpenIdSession(manager,openid,"federatedLoginService/openid/finish") {
            @Override
            protected HttpResponse onSuccess(Identity identity) throws IOException {
                new IdentityImpl(identity).addToCurrentUser();
                return new HttpRedirect("onAssociationSuccess");
            }
        };
        Stapler.getCurrentRequest().getSession().setAttribute(SESSION_NAME,s);
        return s.doCommenceLogin();
    }

    public class IdentityImpl extends FederatedLoginService.FederatedIdentity {
        private final Identity id;

        public IdentityImpl(Identity id) {
            this.id = id;
        }

        @Override
        public String getIdentifier() {
            return id.openId;
        }

        @Override
        public String getNickname() {
            return id.nick;
        }

        @Override
        public String getFullName() {
            return id.fullName;
        }

        @Override
        public String getEmailAddress() {
            return id.email;
        }
    }

    private static final String SESSION_NAME = OpenIdLoginService.class.getName();
}
