package edu.lepturus.ftp.server;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Navigator {
    private final Path ROOT;
    private Path pwd;

    /**
     * 确定一个根目录。调用者需保证这个目录存在。
     *
     * @param rootPath 根目录
     */
    public Navigator(Path rootPath) {
        this.ROOT = rootPath.toAbsolutePath();
        this.pwd = ROOT;
    }

    /**
     * 为区别于NoSuchFileException，定义一个NotFileException以表示“不是一个文件”
     */
    public static class NotFileException extends FileSystemException {
        public NotFileException(String message) {
            super(message);
        }
    }

    /**
     * @return 根目录
     */
    public Path getROOT() {
        return ROOT;
    }

    /**
     * @return 当前工作目录
     */
    public Path getPwd() {
        return ROOT.relativize(pwd);
    }

    /**
     * 解析相对路径。
     * 根据实际情况，可能返回文件或目录。
     * 如果路径在ROOT下但不存在则抛出异常；
     * 不在ROOT下，如果是ROOT的父级目录则转至ROOT，否则抛出异常。
     *
     * @param path 相对路径或绝对路径
     * @return 目标路径
     */
    private Path resolvePath(Path path) throws NoSuchFileException {
        Path resolvedPath = pwd.resolve(path).normalize();
        if (resolvedPath.startsWith(ROOT)) {
            if (Files.exists(resolvedPath)) {
                return resolvedPath;
            } else {
                throw new NoSuchFileException("Target path not exist: " + ROOT.relativize(resolvedPath));
            }
        } else if (ROOT.startsWith(resolvedPath)) {
            return ROOT;
        } else {
            throw new NoSuchFileException("Target path not exist: " + path);
        }
    }

    /**
     * 前往目标目录
     *
     * @param path 相对或绝对路径
     * @throws NoSuchFileException 目标目录不存在
     */
    public void cd(Path path) throws NoSuchFileException {
        Path resolved = resolvePath(path);
        if (Files.isRegularFile(resolved)) {
            resolved = resolved.resolve("..").normalize();
        }
        pwd = resolved;
    }

    /**
     * 列出目录下文件
     *
     * @param path 目标目录
     * @return 目标目录下的文件列表
     * @throws IOException Files.list抛出的异常，或NoSuchFileException
     */
    public List<Path> ls(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(resolvePath(path))) {
            return stream.collect(Collectors.toList());
        } catch (NotDirectoryException e) {
            List<Path> result = new ArrayList<>();
            result.add(resolvePath(path));
            return result;
        }
    }

    /**
     * 列出当前工作目录下文件
     *
     * @return 当前工作目录下的文件列表
     * @throws IOException Files.list抛出的异常，或NoSuchFileException
     */
    public List<Path> ls() throws IOException {
        return ls(pwd);
    }

    /**
     * 获得目标文件
     *
     * @param path 目标文件
     * @return 目标文件
     * @throws NoSuchFileException 文件不存在
     * @throws NotFileException 传入的path不是文件
     */
    public Path get(Path path) throws NoSuchFileException, NotFileException {
        Path file = resolvePath(path);
        if (Files.isRegularFile(file)) {
            return file;
        } else {
            throw new NotFileException("This is not a file!");
        }
    }
}
