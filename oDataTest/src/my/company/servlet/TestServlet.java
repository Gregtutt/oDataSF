package my.company.servlet;

import java.io.IOException;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import my.company.olingo.oData.SFoDataConnect;

import org.apache.olingo.odata2.api.exception.ODataException;

public class TestServlet extends HttpServlet {
    private static final long serialVersionUID = 7336367345358011236L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
	doPost(req, response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
	SFoDataConnect connect = new SFoDataConnect();

	try {
	    connect.initConnectionSuccessFactors();
	} catch (NamingException | ODataException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

	try {
	    response.getWriter().print(connect.getMetaData().toString());
	} catch (ODataException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }
}
