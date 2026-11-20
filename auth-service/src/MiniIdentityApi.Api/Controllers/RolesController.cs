using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MiniIdentityApi.Application.DTOs.Roles;
using MiniIdentityApi.Application.Services;

namespace MiniIdentityApi.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class RolesController : ControllerBase
{
    private readonly RoleService _roleService;

    public RolesController(RoleService roleService)
    {
        _roleService = roleService;
    }

    [Authorize(Roles = "Admin")]
    [HttpGet]
    public IActionResult GetAll()
    {
        return Ok(_roleService.GetAll());
    }

    [Authorize(Roles = "Admin")]
    [HttpPost]
    public IActionResult Create([FromBody] CreateRoleRequest request)
    {
        _roleService.Create(request.Name);
        return StatusCode(StatusCodes.Status201Created, new { message = "Role created." });
    }

    [Authorize(Roles = "Admin")]
    [HttpPost("{roleName}/permissions")]
    public IActionResult AddPermission(string roleName, [FromBody] AddPermissionRequest request)
    {
        _roleService.AddPermission(roleName, request.Code, request.Description);
        return Ok(new { message = "Permission added to role." });
    }
}