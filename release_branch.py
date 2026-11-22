#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script to create or update a release branch in Git and push it to GitHub.
Works on Windows 10 with Python 3.6+ and Git installed and available in PATH.
"""

import subprocess
import sys
import os
from pathlib import Path

# ========== CONFIGURATION ==========
DEFAULT_MAIN_BRANCH = "main"          # Change to "master" if your repo uses that
REMOTE_NAME = "origin"
# ===================================


def run_git(args, cwd, check=True, capture_output=True):
    """
    Execute a Git command and return the result.
    If check=True and the command fails, raise an exception with a clear message.
    """
    cmd = ["git"] + args
    try:
        result = subprocess.run(
            cmd,
            cwd=cwd,
            text=True,
            capture_output=capture_output,
            encoding="utf-8",
            errors="replace"
        )
    except FileNotFoundError:
        raise RuntimeError(
            "The 'git' command was not found. Make sure Git is installed and added to PATH."
        )

    if check and result.returncode != 0:
        error_msg = (result.stderr or result.stdout or "").strip()
        raise RuntimeError(
            f"Command failed: git {' '.join(args)}\n"
            f"Exit code: {result.returncode}\n"
            f"Details: {error_msg}"
        )
    return result


def remote_branch_exists(branch_name, cwd):
    """
    Check if the branch exists on the remote using git ls-remote --exit-code.
    Returns True if it exists, False if it does not.
    If the remote connection fails (network/auth), raises an exception.
    """
    result = run_git(
        ["ls-remote", "--exit-code", "--heads", REMOTE_NAME,
         f"refs/heads/{branch_name}"],
        cwd=cwd,
        check=False
    )
    if result.returncode == 0:
        return True
    elif result.returncode == 2:
        # Exit code 2 means the reference does not exist (branch not on remote)
        return False
    else:
        # Any other code indicates a connection or authentication problem
        err = (result.stderr or result.stdout or "").strip()
        raise RuntimeError(
            f"Could not check branch '{branch_name}' on the remote.\n"
            f"Exit code: {result.returncode}\n"
            f"Details: {err}\n"
            "Check your internet connection and GitHub credentials."
        )


def local_branch_exists(branch_name, cwd):
    """Check if the branch exists locally."""
    result = run_git(
        ["show-ref", "--verify", "--quiet", f"refs/heads/{branch_name}"],
        cwd=cwd,
        check=False
    )
    return result.returncode == 0


def checkout_main(cwd, main_branch):
    """Safely return to the main branch."""
    try:
        run_git(["checkout", main_branch], cwd=cwd, check=True)
        print(f"✅ Returned to main branch '{main_branch}'.")
    except RuntimeError as e:
        print(f"⚠️ Warning: Could not automatically return to '{main_branch}'.")
        print(f"   Reason: {e}")
        print(f"   Please switch back manually: git checkout {main_branch}")


def main():
    print("=" * 60)
    print("Git Release Branch Tool")
    print("=" * 60)

    # ----- 1. Get project path -----
    project_path = input("Enter the project path (e.g., J:\\Android_Application\\My_Smart_Wallet): ").strip()
    if not project_path:
        print("❌ No path entered.")
        sys.exit(1)

    project_dir = Path(project_path).expanduser().resolve()
    if not project_dir.is_dir():
        print(f"❌ Path does not exist or is not a directory: {project_dir}")
        sys.exit(1)

    # Verify it is a Git repository
    if not (project_dir / ".git").exists():
        print(f"❌ Directory is not a Git repository: {project_dir}")
        print("   Make sure the .git folder exists inside the project.")
        sys.exit(1)

    print(f"📁 Project path: {project_dir}")

    # ----- 2. Get version number -----
    version = input("Enter the version number (e.g., 1.6.4): ").strip()
    if not version:
        print("❌ No version number entered.")
        sys.exit(1)

    print(f"🏷️ Version: {version}")

    # ----- 3. Check if branch exists on remote and locally -----
    print("\n🔍 Checking if the branch already exists...")
    try:
        exists_on_remote = remote_branch_exists(version, cwd=project_dir)
    except RuntimeError as e:
        print(f"❌ Error while checking the remote:\n{e}")
        sys.exit(1)

    exists_locally = local_branch_exists(version, cwd=project_dir)

    if exists_on_remote:
        print(f"ℹ️ Branch '{version}' already exists on the remote.")
        branch_action = "update existing branch"
    elif exists_locally:
        print(f"ℹ️ Branch '{version}' exists locally but not on the remote.")
        branch_action = "push existing local branch"
    else:
        print(f"ℹ️ Branch '{version}' does not exist. It will be created.")
        branch_action = "create new branch"

    # ----- 4. Prepare the branch (checkout or create) -----
    try:
        if exists_locally:
            print(f"🔄 Switching to local branch '{version}'...")
            run_git(["checkout", version], cwd=project_dir)
        else:
            print(f"➕ Creating new branch '{version}' and switching to it...")
            run_git(["checkout", "-b", version], cwd=project_dir)

        # ----- 5. Push to remote -----
        print(f"🚀 Pushing branch '{version}' to '{REMOTE_NAME}'...")
        run_git(["push", "-u", REMOTE_NAME, version], cwd=project_dir)
        print(f"✅ Branch '{version}' pushed successfully.")

    except RuntimeError as e:
        print(f"\n❌ Git step failed:\n{e}")
        print("\n🔧 Suggested fixes:")
        print("   - Authentication error: Verify your GitHub credentials (Personal Access Token).")
        print("   - 'rejected' or 'non-fast-forward': You may need to pull changes first or use force (with caution).")
        print("   - Network error: Check your internet connection.")
        exit_code = 1
    else:
        exit_code = 0
    finally:
        # ----- 6. Always return to the main branch -----
        print("\n" + "-" * 40)
        checkout_main(project_dir, DEFAULT_MAIN_BRANCH)

    print("\n" + "=" * 60)
    if exit_code == 0:
        print("🎉 Operation completed successfully.")
    else:
        print("⚠️ Operation finished with errors. Please review the messages above.")
    print("=" * 60)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()