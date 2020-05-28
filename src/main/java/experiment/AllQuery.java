package experiment;

import Main.MethodInitialization;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import config.Constant;
import entity.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import run.Runner;
import script.TmpInfo;
import service.AllQueryService;

import util.BuildFilePath;
import util.FastJsonParseUtil;
import util.FileIO;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AllQuery {

    private Subject subject;
    private Map<String, Boolean> patchCorrectnessMap = new HashMap<>();

    public AllQuery(Subject subject){
        this.subject = subject;
    }

    public boolean containsCorretPatch() {
        String correctPatchString = FileIO.readFileToString(Constant.CorrectPatchInfo + "patchinfo.json");
        List<CorrectPatch> correctPatchList = JSONObject.parseArray(correctPatchString, CorrectPatch.class);
        boolean contains = false;
        List<PatchFile> patchFileList = subject.getPatchList();
        for (PatchFile patchFile : patchFileList) {
            CorrectPatch correctPatch = new CorrectPatch(patchFile.getPatchName(), subject.getName(),
                    String.valueOf(subject.getId()));
            if (correctPatchList.contains(correctPatch)) {
                patchFile.setCorrectness(true);
                patchCorrectnessMap.put(patchFile.getPatchName(), true);
                contains = true;
            }else{
                patchCorrectnessMap.put(patchFile.getPatchName(), false);
            }
        }
        return contains;
    }

    public void queryProcessByOrder() {

        for(int strategy = 1; strategy <= 7; strategy++){

            log.info("current strategy: " + strategy);
            StringBuilder resultBuilder = new StringBuilder();
            String resultPath = "./queryNumber_stra" + strategy  +  ".csv";

            AllQueryService queryService = new AllQueryService(subject);

            queryService.initCurrentPatches();
            resultBuilder.append(subject.getName()).append(subject.getId()).append(",");
            if(containsCorretPatch()){
                // resultBuilder.append("Contain").append(",");
            }else {
                // resultBuilder.append("NotContain").append(",");
            }
            resultBuilder.append(queryService.getCurrentPatches().size()).append(",");

            int number = subject.getFailedTestList().size() < Constant.FailingTestNumber ?
                    subject.getFailedTestList().size():Constant.FailingTestNumber;

            int queryNumber = 0;

            for(int i = 0; i < number; i++) {
                queryService.initQuestionList(subject.getFailedTestList().get(i),true, true, true );
                while (!queryService.isTerminate()) {
                    queryNumber++;
                    Line currentLine = queryService.pickByOrder(strategy);
                    log.info("----------- QueryNumber: " + queryNumber);
                    log.info("current question: {}", currentLine.toString());
                    boolean isTrue = false;
                    if(containsCorretPatch()){
                        isTrue=  getAnswer(currentLine);
                    }else{
                        isTrue = getAnswerForFixed(subject.getFailedTestList().get(i), currentLine);
                    }

                    log.info("Answer: " + isTrue);
                    if (!isTrue) {
                        queryService.processAfterWrongTrace(currentLine);
                    } else {
                        queryService.processAfterRightTrace(currentLine);
                    }
                    log.info("QuestionList Size: " + queryService.getQuestionList().size());
                    log.info("PatchList Size: " + queryService.getCurrentPatches().size());
                    log.info("QueryNumber: " + queryNumber);
                    // resultBuilder.append(queryService.getCurrentPatches().size()).append(",");
                }
            }
            resultBuilder.append(queryService.getCurrentPatches().size()).append(",");
            resultBuilder.append(queryNumber);

            resultBuilder.append("\n");
            FileIO.writeStringToFile(resultPath, resultBuilder.toString(), true);

        }

    }


    public void queryProcess(boolean modificationMethod, boolean variable, boolean trace) {
        StringBuilder resultBuilder = new StringBuilder();
        String resultPath = "./queryNumber.csv";
        //String resultPath = "./remainPatch.csv";

        AllQueryService queryService = new AllQueryService(subject);

        queryService.initCurrentPatches();
       resultBuilder.append(subject.getName()).append(subject.getId()).append(",");
       if(containsCorretPatch()){
          // resultBuilder.append("Contain").append(",");
       }else {
          // resultBuilder.append("NotContain").append(",");
       }
        resultBuilder.append(queryService.getCurrentPatches().size()).append(",");

        int number = subject.getFailedTestList().size() < Constant.FailingTestNumber ?
                subject.getFailedTestList().size():Constant.FailingTestNumber;

        int queryNumber = 0;

        for(int i = 0; i < number; i++) {
            queryService.initQuestionList(subject.getFailedTestList().get(i),modificationMethod, variable, trace );
            while (!queryService.isTerminate()) {
                queryNumber++;
                Line currentLine = queryService.pickOne();
                log.info("----------- QueryNumber: " + queryNumber);
                log.info("current question: {}", currentLine.toString());
                boolean isTrue = false;
                if(containsCorretPatch()){
                  isTrue=  getAnswer(currentLine);
                }else{
                    isTrue = getAnswerForFixed(subject.getFailedTestList().get(i), currentLine);
                }

                log.info("Answer: " + isTrue);
                if (!isTrue) {
                    queryService.processAfterWrongTrace(currentLine);
                } else {
                    queryService.processAfterRightTrace(currentLine);
                }
                log.info("QuestionList Size: " + queryService.getQuestionList().size());
                log.info("PatchList Size: " + queryService.getCurrentPatches().size());
                log.info("QueryNumber: " + queryNumber);
               // resultBuilder.append(queryService.getCurrentPatches().size()).append(",");
            }
        }
        resultBuilder.append(queryService.getCurrentPatches().size()).append(",");
        resultBuilder.append(queryNumber);
      /*  while(queryNumber <= 15){
            resultBuilder.append(queryService.getCurrentPatches().size()).append(",");
            queryNumber++;
        }*/
        resultBuilder.append("\n");
        FileIO.writeStringToFile(resultPath, resultBuilder.toString(), true);
    }

    private boolean getAnswerForFixed(String failingTest, Line currentLine) {
        boolean isTrue = false;
            if (currentLine instanceof LineInfo) {
                if(!new File(BuildFilePath.tmpMapLineIndex(subject, failingTest)).exists()){
                    return false;
                }
                int index = Integer.parseInt(FileIO.readFileToString(BuildFilePath.tmpMapLineIndex(subject, failingTest)));
                if(index == -1){
                    return false;
                }
                String fixedTrace = BuildFilePath.tmpMapTraceLine(Constant.INSTRUMENT_FIXED_SEPARATORINIT,subject, failingTest);
                String line = ((LineInfo) currentLine).getLineName();
                String content = FileIO.readFileToString(fixedTrace);
                String splitLine = content.split("\n")[0];
                String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
                for (String key : fbsArr) {
                    if (splitLine.contains(key)) {
                        splitLine = splitLine.replace(key, "\\" + key);
                    }
                }
                int maxLength = content.split(splitLine+"\n").length;
                if(maxLength < index  ){
                    return false;
                }
                String tmp = content.split(splitLine + "\n")[index-1];
                //String tmp = FileIO.readFileToString(fixedTrace).split(split)[index];
                if(tmp.contains(line)){
                    isTrue = true;
                }else {
                    isTrue = false;
                }

            }else if (currentLine instanceof VariableLine){
                if(!new File(BuildFilePath.tmpStateIndex(subject, failingTest)).exists()){
                    return false;
                }
                int index = Integer.parseInt(FileIO.readFileToString(BuildFilePath.tmpStateIndex(subject, failingTest)));
                if(index == -1){
                    return false;
                }
                String variableFile = BuildFilePath.tmpState(Constant.INSTRUMENT_FIXED_SEPARATORINIT, subject, failingTest);
                String varName =  ((VariableLine) currentLine).getVarName();
                String varValue =  ((VariableLine) currentLine).getValue();
                if(!new File(variableFile).exists()){
                    return false;
                }
                String variableContent = FileIO.readFileToString(variableFile);
                List<JSONObject> variableJsonList = Arrays.stream(variableContent.split("\n"))
                        .filter(StringUtils::isNotBlank)
                        .filter(line -> !(line.endsWith("START#0") || line.endsWith("END#0")))
                        .map(variableLine -> {
                            //System.out.println(variableLine.toString());
                            String[] array = variableLine.split(": \\{", 2);
                            if (array.length >= 2) {
                                array[1] = "{" + array[1];
                                String value = array[1].replaceAll("\\{null\\}", "\\{\\}")
                                        .replaceAll("\\\"<null>\\\"", "null");
                                JSONObject resultJsonObject = FastJsonParseUtil.jsonFormatter(value, array[0]);
                                return resultJsonObject;
                            }
                            return null;
                        })
                        .filter(Objects::nonNull).collect(Collectors.toList());
                JSONObject currentJson = variableJsonList.get(index);
                if(currentJson.getString(varName).equals(varValue)){
                    return true;
                }else {
                    return false;
                }

            }else if (currentLine instanceof LocationLine){
                String methodName = ((LocationLine) currentLine).getModifyMethod();
                String projectid = subject.getName() + subject.getId();
                String content = FileIO.readFileToString(Constant.Correct_Modification);
                for(String line :content.split("\n")){
                    if(line.trim().equals("")){
                        continue;
                    }

                    if(line.split(",",2)[0].equals(projectid)){
                        String methods = line.split(",",2)[1];
                        if(methods.equals("")){
                            return false;
                        }
                        if(methods.contains(";")){
                            String[]  method = line.split(",",2)[1].split(";");
                            for(int i = 0; i< method.length; i++){
                                if(methodName.equals(method[i])){
                                    return  true;
                                }else {
                                    return  false;
                                }
                            }
                        }else{
                            if(methodName.equals(methods)){
                                return true;
                            }else{
                                return false;
                            }
                        }

                    }
                }

            }
            return isTrue;
    }

    private boolean getAnswer(Line currentLine) {
        boolean isTrue = false;
            for (PatchFile patchFile : currentLine.getPatchList()) {
                if (patchCorrectnessMap.get(patchFile.getPatchName())) {
                    isTrue = true;
                    break;
                }
            }

        return isTrue;
    }

    public static void main(String[] args){
        String project = "Math";
        int start = 95;
        int end = 95;
        boolean modificationMethod = true;
        boolean variable = true;
        boolean trace = true;
        int queryStrategy =0;
        for (String arg : args) {
            if (arg.startsWith("-p=")) {
                // Constant.PROJECT_HOME = args[i].substring("--proj_home=".length());
                project = arg.substring("-p=".length());
            } else if (arg.startsWith("-s=")) {
                start = Integer.parseInt(arg.substring("-s=".length()));
            } else if (arg.startsWith("-e=")) {
                end = Integer.parseInt(arg.substring("-e=".length()));
            } else if (arg.startsWith("-t=")){
                if(arg.substring("-t=".length()).equalsIgnoreCase("method")){
                    modificationMethod = false;
                }else  if (arg.substring("-t=".length()).equalsIgnoreCase("variable")){
                    variable = false;
                }else if(arg.substring("-t=".length()).equalsIgnoreCase("trace")){
                    trace = false;
                }
            } else if(arg.startsWith("-stra=")){
                queryStrategy = Integer.parseInt(arg.substring("-stra=".length()));
            }
        }

        for(int times = 0; times < 1; times++){
            log.info("Times: " + times + " Start Running <<" + project +">> AllQuery from " + start + " to " + end );
            for (int i = start; i <= end; i++) {
                Subject subject = new Subject(project, i);
                if (subject.initPatchListByPath(Constant.AllPatchPath)) {
                    log.info("Process " + subject.toString());
                    //FileIO.writeStringToLog(resultFile, "Process " + subject.toString());
                    List<PatchFile> patchList = subject.getPatchList();
                    if(!subject.exist()){
                        if(! Runner.downloadSubject(subject)){
                            continue;
                        }
                    }
                    AllQuery allQuery = new AllQuery(subject);
                    // if(! allQuery.containsCorretPatch()){
                    MethodInitialization methodInitialization = new MethodInitialization(subject);
                    methodInitialization.MainProcess();
                   // allQuery.queryProcess(modificationMethod, variable, trace);
                    allQuery.queryProcessByOrder();
                    //  }
                }
            }
        }

    }
}


