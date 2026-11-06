import contextlib
import io
import os
import site
import sys
import traceback

def _site_dir(app_files):
    path = os.path.join(app_files, "python-packages")
    os.makedirs(path, exist_ok=True)
    if path not in sys.path:
        site.addsitedir(path)
    return path

def run(code, app_files):
    _site_dir(app_files)
    buf = io.StringIO()
    try:
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            exec(code, {"__name__": "__main__"})
    except BaseException:
        traceback.print_exc(file=buf)
    return buf.getvalue()

def pip_command(args, app_files=None):
    if app_files is None:
        app_files = os.path.dirname(os.path.dirname(__file__))
    target = _site_dir(app_files)
    parts = args.split()
    buf = io.StringIO()
    try:
        from pip._internal.cli.main import main as pip_main
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            if parts[:1] == ["list"]:
                import importlib.metadata
                for dist in sorted(importlib.metadata.distributions(path=[target]),
                                   key=lambda d: (d.metadata.get("Name") or "").lower()):
                    print(f"{dist.metadata.get('Name', 'unknown')} {dist.version}")
                return buf.getvalue() or "No runtime-installed packages."
            if parts[:1] == ["show"] and len(parts) > 1:
                return str(pip_main(["show", "--path", target, *parts[1:]]))
            if not parts or parts[0] != "install":
                return "Usage: pip install <package> | pip list"
            result = pip_main(["install", "--disable-pip-version-check", "--target", target, *parts[1:]])
            if result == 0:
                site.addsitedir(target)
                return buf.getvalue() + "Installed into " + target
            return buf.getvalue() + f"pip exited with status {result}"
    except BaseException:
        traceback.print_exc(file=buf)
        return buf.getvalue()
