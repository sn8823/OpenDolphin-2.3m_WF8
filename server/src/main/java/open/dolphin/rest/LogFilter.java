package open.dolphin.rest;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.mbean.ServletContextHolder;
import open.dolphin.session.UserServiceBean;

/**
 * LogFilter
 * 
 * @author Kazushi Minagawa, Digital Globe, Inc.
 * @author modified by masuda, Masuda Naika
 */
@WebFilter(urlPatterns = {"/rest/*"}, asyncSupported = true)
public class LogFilter implements Filter {
    
    private static final boolean WARN = false;
    private static final boolean INFO = true;

    private static final Logger logger = Logger.getLogger(LogFilter.class.getSimpleName());

    private static final String UNAUTHORIZED_USER = "Unauthorized user: ";

    @Inject
    private UserServiceBean userService;
    
    @Inject
    private ServletContextHolder contextHolder;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        
        String fid = null;
        String userName = req.getHeader(IInfoModel.USER_NAME);
        String password = req.getHeader(IInfoModel.PASSWORD);
        
        try {
            int pos = userName.indexOf(IInfoModel.COMPOSITE_KEY_MAKER);
            fid = userName.substring(0, pos);
        } catch (Exception ex) {
        }

        if (fid == null || userName == null || password == null) {
            HttpServletResponse res = (HttpServletResponse) response;
            res.sendError(400);
            return;
        }
        
        Map<String, String> userMap = contextHolder.getUserMap();
        boolean authentication = password.equals(userMap.get(userName));
        
        if (!authentication) {
            userMap.remove(userName);
            String remoteIp = req.getRemoteAddr();
            authentication = userService.authenticate(userName, password, remoteIp);
            if (!authentication) {
                HttpServletResponse res = (HttpServletResponse) response;
                StringBuilder sb = new StringBuilder();
                sb.append(UNAUTHORIZED_USER);
                sb.append(userName).append(": ").append(req.getRequestURI());
                String msg = sb.toString();
                warn(msg);
                res.sendError(401);
                return;
            } else {
                userMap.put(userName, password);
            }
        }
        
        // facilityIdを属性にセットしておく
        req.setAttribute(IInfoModel.FID, fid);

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(req.getRemoteAddr()).append(" ");
        sb.append(userName.substring(17)).append(" ");
        sb.append(req.getMethod()).append(" ");
        sb.append(req.getRequestURI());
        String query = req.getQueryString();
        if (query != null && !query.isEmpty()) {
            sb.append("?").append(query);
        }
        
        String msg = URLDecoder.decode(sb.toString(), "UTF-8"); // TO-DO

        if (msg.length() > 160) {
            msg = msg.substring(0, 157) + ("...");
        }

        info(msg);

        chain.doFilter(req, response);
    }

    @Override
    public void destroy() {
    }
    
    private void warn(String msg) {
        if (WARN) {
            logger.warning(msg);
        }
    }
    
    private void info(String msg) {
        if (INFO) {
            logger.info(msg);
        }
     }
}
