# GAP JupyterLab

This directory contains the GitHub Actions setup for a temporary interactive GAP + JupyterLab session.

## Start

1. Open **Actions** in the repository.
2. Select **GAP JupyterLab**.
3. Click **Run workflow**.
4. Wait for the `Cloudflare tunnel` step.
5. Open the `JUPYTER_URL` printed in the log. The URL already contains the Jupyter token.
6. In JupyterLab create a notebook and choose the **GAP 4** kernel.

Example:

```gap
G := SymmetricGroup(8);
Size(G);
StructureDescription(G);
```

The session runs on a GitHub-hosted Ubuntu runner and is temporary. The runner is destroyed when the workflow ends. The workflow is configured for the GitHub-hosted 6-hour job limit.

Files saved under `gap-jupyter/notebooks` are mounted into the Jupyter container during the session. Changes made inside the running session are not automatically committed back to GitHub.

## Security

The workflow uses a temporary TryCloudflare Quick Tunnel and a Jupyter token. Treat the generated URL as a secret while the session is running. Quick Tunnels are temporary and intended for development/testing.
