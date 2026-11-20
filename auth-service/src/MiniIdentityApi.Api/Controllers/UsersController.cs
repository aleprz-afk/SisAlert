using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MiniIdentityApi.Application.DTOs.Users;
using MiniIdentityApi.Application.Services;

namespace MiniIdentityApi.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class UsersController : ControllerBase
{
    private readonly UserService _userService;

    public UsersController(UserService userService)
    {
        _userService = userService;
    }

    [Authorize(Roles = "Admin")]
    [HttpGet]
    public IActionResult GetAll()
    {
        return Ok(_userService.GetAll());
    }

    [Authorize]
    [HttpGet("{id:guid}")]
    public IActionResult GetById(Guid id)
    {
        var user = _userService.GetById(id);
        return user is null ? NotFound() : Ok(user);
    }

    [Authorize(Roles = "Admin")]
    [HttpPut("{id:guid}")]
    public IActionResult Update(Guid id, [FromBody] UpdateUserRequest request)
    {
        var user = _userService.Update(id, request.Username, request.Email);
        return Ok(user);
    }

    [Authorize(Roles = "Admin")]
    [HttpPatch("{id:guid}/activate")]
    public IActionResult Activate(Guid id)
    {
        _userService.Activate(id);
        return Ok(new { message = "User activated." });
    }

    [Authorize(Roles = "Admin")]
    [HttpPatch("{id:guid}/deactivate")]
    public IActionResult Deactivate(Guid id)
    {
        _userService.Deactivate(id);
        return Ok(new { message = "User deactivated." });
    }

    [Authorize(Roles = "Admin")]
    [HttpPost("{id:guid}/roles")]
    public IActionResult AssignRole(Guid id, [FromBody] AssignRoleRequest request)
    {
        _userService.AssignRole(id, request.RoleName);
        return Ok(new { message = "Role assigned." });
    }

    [Authorize(Roles = "Admin")]
    [HttpDelete("{id:guid}")]
    public IActionResult Delete(Guid id)
    {
        _userService.Delete(id);
        return NoContent();
    }
}
