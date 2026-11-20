using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace MiniIdentityApi.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class DemoController : ControllerBase
{
    [Authorize]
    [HttpGet("profile")]
    public IActionResult Profile()
    {
        return Ok(new { message = "Authenticated endpoint." });
    }

    [Authorize(Roles = "Admin")]
    [HttpGet("admin")]
    public IActionResult AdminOnly()
    {
        return Ok(new { message = "Admin endpoint." });
    }
}