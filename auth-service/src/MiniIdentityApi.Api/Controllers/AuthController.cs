using Microsoft.AspNetCore.Mvc;
using MiniIdentityApi.Application.DTOs.Auth;
using MiniIdentityApi.Application.Services;

namespace MiniIdentityApi.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AuthenticationService _authenticationService;

    public AuthController(AuthenticationService authenticationService)
    {
        _authenticationService = authenticationService;
    }

    [HttpPost("register")]
    public IActionResult Register([FromBody] RegisterRequest request)
    {
        _authenticationService.Register(request);
        return StatusCode(StatusCodes.Status201Created, new { message = "User registered successfully." });
    }

    [HttpPost("login")]
    public ActionResult<AuthResponse> Login([FromBody] LoginRequest request)
    {
        var response = _authenticationService.Login(request);
        return Ok(response);
    }
}