"""
Simple Android Automation ReAct Agent
Just 2 tools: ADB commands + Package checker
"""

import json
import subprocess
from ollama import chat, ChatResponse
from typing import Dict, Any


# ============= TOOL 1: Run ADB Commands =============

def run_adb_command(command: str) -> Dict[str, Any]:
    """
    Execute any ADB command.
    Examples:
    - "devices" - check connected devices
    - "shell pm list packages" - list all packages
    - Your automation test commands
    """
    try:
        result = subprocess.run(
            f'adb  {command}',
            capture_output=True,
            text=True,
            timeout=30,
            shell=True
        )

        return {
            "command": f"adb {command}",
            "output": result.stdout.strip(),
            "error": result.stderr.strip() if result.stderr else None,
            "success": result.returncode == 0
        }
    except Exception as e:
        return {"error": f"Failed to run command: {str(e)}"}


# ============= TOOL 2: Download and Install Latest Build =============

def get_and_install_latest_build(
    organization: str,
    project: str,
    pipeline_id: str,
    pat_token: str,
    artifact_name: str = "drop",
    branch: str = "refs/heads/main"
) -> Dict[str, Any]:
    """
    Get the latest successful build from specified branch and install it via ADB.

    Steps:
    1. Query Azure DevOps for latest successful build from branch
    2. Download the build artifact (APK)
    3. Install via ADB to connected device

    Args:
        organization: Azure DevOps organization name
        project: Project name
        pipeline_id: Pipeline ID or definition ID
        pat_token: Personal Access Token for authentication
        artifact_name: Name of the artifact containing APK (default: "drop")
        branch: Branch name (default: "refs/heads/main")

    Returns: Installation status with build info
    """
    try:
        import requests
        from base64 import b64encode
        import tempfile
        import os
        import zipfile

        # Step 1: Get latest successful build from branch
        url = f"https://dev.azure.com/{organization}/{project}/_apis/build/builds"
        params = {
            "definitions": pipeline_id,
            "branchName": branch,
            "resultFilter": "succeeded",
            "$top": 1,
            "api-version": "7.0"
        }

        auth_header = b64encode(f":{pat_token}".encode()).decode()
        headers = {
            "Authorization": f"Basic {auth_header}",
            "Content-Type": "application/json"
        }

        response = requests.get(url, params=params, headers=headers, timeout=10)

        if response.status_code != 200:
            return {
                "error": f"Failed to get builds: {response.status_code}",
                "details": response.text
            }

        data = response.json()

        if not data.get("value"):
            return {"error": f"No successful builds found for branch {branch}"}

        latest_build = data["value"][0]
        build_id = latest_build.get("id")
        build_number = latest_build.get("buildNumber")

        # Step 2: Get artifact download URL
        artifact_url = f"https://dev.azure.com/{organization}/{project}/_apis/build/builds/{build_id}/artifacts"
        artifact_params = {"artifactName": artifact_name, "api-version": "7.0"}

        artifact_response = requests.get(artifact_url, params=artifact_params, headers=headers, timeout=10)

        if artifact_response.status_code != 200:
            return {
                "error": f"Failed to get artifact: {artifact_response.status_code}",
                "build_id": build_id,
                "build_number": build_number
            }

        artifact_data = artifact_response.json()

        if not artifact_data.get("value"):
            return {
                "error": f"Artifact '{artifact_name}' not found in build {build_number}",
                "available_artifacts": [a.get("name") for a in artifact_data.get("value", [])]
            }

        download_url = artifact_data["value"][0].get("resource", {}).get("downloadUrl")

        if not download_url:
            return {"error": "Download URL not found in artifact response"}

        # Step 3: Download artifact
        download_response = requests.get(download_url, headers=headers, timeout=60)

        if download_response.status_code != 200:
            return {"error": f"Failed to download artifact: {download_response.status_code}"}

        # Step 4: Save and extract artifact
        with tempfile.TemporaryDirectory() as temp_dir:
            zip_path = os.path.join(temp_dir, "artifact.zip")

            with open(zip_path, "wb") as f:
                f.write(download_response.content)

            # Extract APK
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(temp_dir)

            # Find APK file
            apk_file = None
            for root, _, files in os.walk(temp_dir):
                for file in files:
                    if file.endswith('.apk'):
                        apk_file = os.path.join(root, file)
                        break
                if apk_file:
                    break

            if not apk_file:
                return {
                    "error": "No APK file found in artifact",
                    "build_number": build_number,
                    "downloaded": True
                }

            # Step 5: Install via ADB
            install_result = subprocess.run(
                f'adb install -r "{apk_file}"',
                capture_output=True,
                text=True,
                timeout=60,
                shell=True
            )

            return {
                "success": install_result.returncode == 0,
                "build_id": build_id,
                "build_number": build_number,
                "branch": branch,
                "apk_file": os.path.basename(apk_file),
                "install_output": install_result.stdout.strip(),
                "install_error": install_result.stderr.strip() if install_result.stderr else None,
                "web_url": latest_build.get("_links", {}).get("web", {}).get("href")
            }

    except ImportError:
        return {"error": "Missing dependency. Install: pip install requests"}
    except subprocess.TimeoutExpired:
        return {"error": "ADB install timeout - check device connection"}
    except Exception as e:
        return {"error": f"Failed to download and install: {str(e)}"}


# ============= TOOL DEFINITIONS =============

tools = [
    {
        "type": "function",
        "function": {
            "name": "run_adb_command",
            "description": "Run any ADB command. Use this to check devices, run automation tests, or any adb command.",
            "parameters": {
                "type": "object",
                "properties": {
                    "command": {
                        "type": "string",
                        "description": "The ADB command without 'adb' prefix. Examples: 'devices -l' to list all connected devices with details, 'shell pm list packages' to list packages",
                    }
                },
                "required": ["command"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_and_install_latest_build",
            "description": "Download the latest successful build from main branch (or specified branch) and install it via ADB. This combines getting the build artifact and installation in one step.",
            "parameters": {
                "type": "object",
                "properties": {
                    "organization": {
                        "type": "string",
                        "description": "Azure DevOps organization name (e.g., 'mycompany')",
                    },
                    "project": {
                        "type": "string",
                        "description": "Project name in Azure DevOps",
                    },
                    "pipeline_id": {
                        "type": "string",
                        "description": "Pipeline ID or definition ID (numeric or name)",
                    },
                    "pat_token": {
                        "type": "string",
                        "description": "Personal Access Token for Azure DevOps authentication",
                    },
                    "artifact_name": {
                        "type": "string",
                        "description": "Name of the artifact containing APK (default: 'drop')",
                    },
                    "branch": {
                        "type": "string",
                        "description": "Branch name to get build from (default: 'refs/heads/main')",
                    }
                },
                "required": ["organization", "project", "pipeline_id", "pat_token"],
            },
        },
    },
]

available_functions = {
    "run_adb_command": run_adb_command,
    "get_and_install_latest_build": get_and_install_latest_build,
}


# ============= REACT AGENT =============

class AndroidReactAgent:
    """Simple ReAct agent for Android automation testing."""

    def __init__(self, model: str = "gpt-oss:20b"):
        self.model = model
        self.max_iterations = 10

    def run(self, user_query: str) -> str:
        """Run the ReAct loop."""
        messages = [{"role": "user", "content": user_query}]
        iteration = 0

        print(f"\n{'='*50}")
        print(f"Query: {user_query}")
        print(f"{'='*50}\n")

        while iteration < self.max_iterations:
            iteration += 1
            print(f"\n--- Iteration {iteration} ---")

            response: ChatResponse = chat(
                self.model,
                messages=messages,
                tools=tools,
            )

            if response.message.tool_calls:
                # Add assistant message
                messages.append(response.message)

                # Execute tools
                for tool_call in response.message.tool_calls:
                    func_name = tool_call.function.name
                    func_args = tool_call.function.arguments

                    print(f"Tool: {func_name}({func_args})")

                    func = available_functions[func_name]
                    result = func(**func_args)

                    print(f"Result: {json.dumps(result, indent=2)}\n")

                    # Add tool response
                    messages.append({
                        "role": "tool",
                        "content": json.dumps(result),
                    })
                continue

            else:
                # Final answer
                answer = response.message.content
                print(f"\n{'='*50}")
                print(f"Answer: {answer}")
                print(f"{'='*50}\n")
                return answer

        return "Error: Max iterations reached"


# ============= EXAMPLES =============

def main():
    agent = AndroidReactAgent()

    # Example 1: Check devices
    agent.run("What devices are connected?")

    # Example 2: Download and install latest build from main branch
    # Note: Replace with your actual Azure DevOps details
    agent.run("""
        Download and install the latest successful build from main branch.
        Organization: myorg
        Project: MyProject
        Pipeline ID: 123
        PAT Token: your_pat_token_here
        Artifact name: drop
    """)


if __name__ == "__main__":
    print("Android Automation ReAct Agent")
    print("Requirements: ollama with gpt-oss:20b, adb, pip install requests\n")
    main()
