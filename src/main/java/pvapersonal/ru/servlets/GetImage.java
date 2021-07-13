package pvapersonal.ru.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pvapersonal.ru.other.FilesHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@WebServlet(urlPatterns = {"/images/*"})
public class GetImage extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String fileName = req.getRequestURL().substring(req.getRequestURL().lastIndexOf("/")+1,
                req.getRequestURL().length());
        if(fileName.contains("\\?")){
            fileName = fileName.split("\\?")[0];
        }
        File image = FilesHandler.getFile(fileName);
        if(image != null){
            resp.setContentType("image/" + fileName.split("\\.")[fileName.split("\\.").length - 1]);
            byte[] imgBytes = new FileInputStream(image).readAllBytes();
            resp.getOutputStream().write(imgBytes);
        }else{
            resp.setStatus(404);
            resp.setContentType("image/jpg");
        }
    }
}
