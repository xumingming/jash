package jash.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.options.MutableDataHolder;
import com.vladsch.flexmark.util.options.MutableDataSet;
import jash.Application;
import jash.parser.Project;
import jash.parser.ProjectStat.PercentageStat;
import jash.parser.Task;
import jash.service.PlanService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

import static jash.Utils.getCurrentDirectoryPath;
import static jash.Utils.getCurrentFilePath;
import static jash.Utils.getFileDisplayName;

@Controller
public class ProjectController {
    @Autowired
    private PlanService planService;

    @RequestMapping(value = "/**/_")
    public String directory(HttpServletRequest req, Model model) throws Exception {
        String filePath = getCurrentDirectoryPath(req);
        List<FileVO> fileVOs = Arrays
            .stream(
                new File(filePath)
                    .listFiles(f -> !f.getName().startsWith("."))
            )
            .map(
                f -> new FileVO(
                    getFileDisplayName(f),
                    f.getAbsolutePath().substring(Application.ROOT.length()),
                    f.isDirectory()
                )
            )
            .sorted((x, y) -> {
                if (x.isDir() && !y.isDir()) {
                    return -1;
                } else if (!x.isDir() && y.isDir()){
                    return 1;
                } else {
                    return y.getName().compareTo(x.getName());
                }
            })
            .collect(Collectors.toList());

        model.addAttribute("files", fileVOs);
        model.addAttribute("breadcrumb", new BreadcrumVO(Application.ROOT, filePath));
        return "directory";
    }

    @RequestMapping(value = "/**/*.plan.md.json")
    public @ResponseBody
    PercentageStat projectJson(HttpServletRequest req) throws Exception {
        String filePath = getCurrentFilePath(req);
        filePath = filePath.substring(0, filePath.length() - 5);
        Project project = planService.getProject(filePath);
        return project.getStat().getNotFinishedStat();
    }

    @RequestMapping(value = "/**/*.plan.md")
    public String project(HttpServletRequest req,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String man,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "false") boolean reverse,
        Model model) throws Exception {
        String filePath = getCurrentFilePath(req);
        List<String> cleanedKeywords = new ArrayList<>();
        if (StringUtils.isNotBlank(keyword)) {
            String[] keywords = keyword.split("\\|");
            cleanedKeywords = Arrays.asList(keywords).stream()
                .map(x -> x.trim()).collect(Collectors.toList());
        }
        Project project = planService.getProject(filePath, man, status, cleanedKeywords, reverse);
        model.addAttribute("path",
            req.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE));

        model.addAttribute("fullProject", Converters.convert(planService.getProject(filePath)));
        model.addAttribute("project", Converters.convert(project));
        model.addAttribute("selectedMan", man);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedKeyword", keyword);
        model.addAttribute("selectedReverse", reverse);
        model.addAttribute("breadcrumb", new BreadcrumVO(Application.ROOT, filePath));
        model.addAttribute("article", renderMarkdown(filePath));

        return "project";
    }

    @RequestMapping(path = {"/**/*.md"}, produces = "text/html")
    public String markdown(Model model, HttpServletRequest req) throws Exception {
        String filePath = getCurrentFilePath(req);

        String html = renderMarkdown(filePath);
        model.addAttribute("article", html);
        model.addAttribute("breadcrumb", new BreadcrumVO(Application.ROOT, filePath));
        return "markdown";
    }

    private String renderMarkdown(String filePath) throws IOException {
        final MutableDataHolder OPTIONS = new MutableDataSet()
            .set(HtmlRenderer.INDENT_SIZE, 2)
            .set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
            .set(
                com.vladsch.flexmark.parser.Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(),
                    StrikethroughExtension.create())
            )
            ;
        com.vladsch.flexmark.parser.Parser parser =
            com.vladsch.flexmark.parser.Parser.builder(OPTIONS).build();
        Node document = parser.parse(
            FileUtils.readLines(new File(filePath), "UTF-8")
            .stream().collect(Collectors.joining("\n"))
        );
        HtmlRenderer renderer = HtmlRenderer.builder(OPTIONS).build();
        return renderer.render(document);
    }
}
