import os

from fastapi import Header, HTTPException

APP_PASSWORD = os.environ.get("LABNOTEBOOK_PASSWORD", "").strip()


def require_auth(x_auth_token: str = Header(default="")) -> None:
    """Optional single-password gate.

    If LABNOTEBOOK_PASSWORD is unset, the app runs open (local personal use).
    Set it to require the matching X-Auth-Token header on every API call --
    useful once the app is deployed somewhere reachable over the internet.
    """
    if not APP_PASSWORD:
        return
    if x_auth_token != APP_PASSWORD:
        raise HTTPException(status_code=401, detail="Invalid or missing auth token")
